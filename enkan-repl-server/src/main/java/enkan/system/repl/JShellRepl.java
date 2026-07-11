package enkan.system.repl;

import enkan.Env;
import enkan.exception.FalteringEnvironmentException;
import enkan.system.*;
import enkan.system.command.*;
import enkan.system.repl.jshell.CompletionServer;
import enkan.system.repl.jshell.JShellIoProxy;
import enkan.system.repl.jshell.JShellObjectTransferer;
import enkan.system.repl.jshell.SystemIoTransport;
import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static enkan.system.ReplResponse.ResponseStatus.DONE;
import static enkan.system.ReplResponse.ResponseStatus.SHUTDOWN;

/**
 * The REPL (Read-Eval-Print Loop) implementation using JShell.
 * @author kawasima
 */
public class JShellRepl implements Repl {
    private static final Logger LOG = LoggerFactory.getLogger(JShellRepl.class);

    private final JShell jshell;
    private final JShellIoProxy ioProxy;
    private final ExecutorService threadPool;
    private final Map<String, SystemCommand> localCommands = new HashMap<>();
    private final Map<String, SystemCommand> allCommands = new HashMap<>();
    private final Map<String, Future<?>> backgroundTasks = new ConcurrentHashMap<>();
    private final List<TransportProvider> transportProviders = new ArrayList<>();
    private final CompletableFuture<Integer> replPort = new CompletableFuture<>();
    /** True when the REPL is bound only to loopback — local commands are safe to run. */
    private boolean localOnly;

    private JShellMessage executeStatement(String statement) {
        statement = statement.trim();
        if (statement.endsWith(";")) statement = statement.replaceAll(";+$", "");

        List<SnippetEvent> events = jshell.eval(statement + ";");
        JShellMessage msg = new JShellMessage();
        for (SnippetEvent event: events) {
            switch(event.status()) {
                case VALID:
                    if (event.exception() != null) {
                        StringWriter sw = new StringWriter();
                        event.exception().printStackTrace(new PrintWriter(sw));
                        msg.errs.addAll(Arrays.asList(sw.toString().split(System.lineSeparator())));
                    } else {
                        msg.outs.add(event.value());
                    }
                    break;
                case NONEXISTENT:
                    LOG.info("NONEXISTENT:{}", statement);
                    break;
                case REJECTED:
                    jshell.diagnostics(event.snippet())
                            .forEach(diag -> {
                                if (diag.isError()) {
                                    msg.errs.add(diag.getMessage(Locale.getDefault()));
                                } else {
                                    msg.outs.add(diag.getMessage(Locale.getDefault()));
                                }
                            });
                    break;
                default:
                    LOG.warn("{}:{}", event.status(), statement);
            }
        }
        return msg;
    }

    public JShellRepl(String enkanSystemFactoryClassName) {
        try {
            ioProxy = new JShellIoProxy();
            ioProxy.start();
            jshell = JShell.builder()
                    .out(ioProxy.forJShellPrintStream())
                    .err(ioProxy.forJShellErrorStream())
                    .executionEngine("local")
                    .build();
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl instanceof URLClassLoader urlCl) {
                URL[] urls = urlCl.getURLs();
                Arrays.stream(urls)
                        .map(URL::toString)
                        .forEach(jshell::addToClasspath);
            } else {
                String[] paths = System.getProperty("java.class.path").split(":");
                Arrays.stream(paths).forEach(jshell::addToClasspath);
            }
            // Add target/classes to classpath
            String userDir = System.getProperty("user.dir");
            jshell.addToClasspath(userDir + "/target/classes");
            executeStatement("import java.util.*");
            executeStatement("import enkan.system.*");
            executeStatement("import enkan.config.EnkanSystemFactory");
            executeStatement("import enkan.system.repl.jshell.JShellObjectTransferer");
            executeStatement("import enkan.system.repl.jshell.SystemIoTransport");
            executeStatement("var system = ((Class<? extends EnkanSystemFactory>)Class.forName(\"" + enkanSystemFactoryClassName + "\")).getConstructor().newInstance().create()");
            executeStatement("var transport = new SystemIoTransport()");
            executeStatement("var __commands = new HashMap<String, SystemCommand>()");

            threadPool = Executors.newCachedThreadPool(runnable -> {
                Thread t = new Thread(runnable);
                t.setName("enkan-repl-jshell");
                return t;
            });
            registerCommand("start", new StartCommand());
            registerCommand("stop",  new StopCommand());
            registerCommand("reset", new ResetCommand());
            registerCommand("middleware", new MiddlewareCommand());
            registerCommand("status", new StatusCommand());
            // help is registered as a local command so it always reflects the live allCommands map
            registerLocalCommand("help", new HelpCommand(allCommands));
            // Import component classes from the system already created inside JShell
            JShellMessage importMsg = executeStatement(
                    "system.getAllComponents().stream().map(c -> c.getClass().getName()).collect(java.util.stream.Collectors.joining(\",\"))");
            if (!importMsg.outs.isEmpty()) {
                String classNames = importMsg.outs.get(0);
                if (classNames != null && !classNames.isBlank()) {
                    Arrays.stream(classNames.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(name -> executeStatement("import " + name));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eval(String statement, Transport transport) {
        JShellMessage msg = executeStatement(statement);
        msg.errs.forEach(line -> transport.send(ReplResponse.withErr(line)));
        msg.outs.forEach(line -> transport.send(ReplResponse.withOut(line)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTransportProvider(TransportProvider provider) {
        transportProviders.add(provider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(String command) {
        String[] cmds = command.trim().split("\\s+");
        dispatchCommand(cmds, new SystemIoTransport(), -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerLocalCommand(String name, SystemCommand command) {
        localCommands.put(name, command);
        allCommands.put(name, command);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerCommand(String name, SystemCommand command) {
        try {
            String serializedCommand = JShellObjectTransferer.writeToBase64(command);
            executeStatement("__commands.put(\"" + name
                    + "\", JShellObjectTransferer.readFromBase64(\""
                    + serializedCommand
                    + "\", SystemCommand.class))");
        } catch (Exception e) {
            throw new IllegalArgumentException("command cannot be serialized:" + command, e);
        }
        allCommands.put(name, command);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addBackgroundTask(String name, Runnable task) {
        Future<?> current = getBackground(name);
        if (current != null) {
            return;
        }
        Future<?> future = threadPool.submit(() -> {
            try {
                task.run();
            } finally {
                backgroundTasks.remove(name);
            }
        });
        backgroundTasks.put(name, future);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getPort() {
        try {
            return replPort.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new FalteringEnvironmentException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Future<?> getBackground(String name) {
        Future<?> future = backgroundTasks.get(name);
        if (future != null && (future.isCancelled() || future.isDone())) {
            backgroundTasks.remove(name);
            return null;
        }
        return future;
    }

    /**
     * Escapes a string literal for safe embedding inside a JShell double-quoted string expression.
     * Handles backslashes, double-quotes, and control characters (newline, carriage-return, tab).
     */
    private static String escapeForJShellString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private void executeCommand(String commandName, String[] args, Transport transport) {
        try {
            String argStr = Arrays.stream(args)
                    .map(arg -> "\"" + escapeForJShellString(arg) + "\"")
                    .collect(Collectors.joining(","));
            StringBuilder execStatement = new StringBuilder("__commands.get(\"")
                    .append(escapeForJShellString(commandName))
                    .append("\").execute(system, transport");
            if (!argStr.isEmpty()) {
                execStatement.append(",").append(argStr);
            }
            execStatement.append(")");

            JShellMessage evalMessage = executeStatement(execStatement.toString());
            if (!evalMessage.errs.isEmpty()) {
                evalMessage.errs.forEach(line -> transport.send(ReplResponse.withErr(line)));
                transport.sendErr("", DONE);
            } else {
                transport.sendOut("", DONE);
            }
        } catch (Throwable ex) {
            StringWriter traceWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(traceWriter));
            transport.sendErr(traceWriter.toString());
        }
    }

    /**
     * Dispatch a command (slash or plain statement) to the appropriate handler.
     * Shared by both ZMQ and WebSocket entry points.
     *
     * @param cmds      the command tokens (first element may start with "/")
     * @param transport the transport to send responses on
     * @param completerPort the completer port number (for /completer command), or -1 if unavailable
     * @return true if the REPL should shut down
     */
    private boolean dispatchCommand(String[] cmds, Transport transport, int completerPort) {
        if (cmds[0].startsWith("/")) {
            String commandName = cmds[0].substring(1);
            if (commandName.isEmpty()) {
                commandName = "help";
            }
            if (Objects.equals("completer", commandName)) {
                transport.sendOut(Integer.toString(completerPort));
            } else if (Objects.equals("shutdown", commandName)) {
                executeStatement("shutdown.exec(system, transport)");
                transport.sendOut("shutdown", SHUTDOWN);
                ioProxy.stop();
                return true;
            } else if (localCommands.containsKey(commandName)) {
                if (localOnly || commandName.equals("help")) {
                    try {
                        SystemCommand command = localCommands.get(commandName);
                        command.execute(null, transport, Arrays.copyOfRange(cmds, 1, cmds.length));
                    } catch (Exception ex) {
                        StringWriter traceWriter = new StringWriter();
                        ex.printStackTrace(new PrintWriter(traceWriter));
                        transport.sendErr(traceWriter.toString());
                    }
                } else {
                    transport.sendErr("/" + commandName + " is a local-only command and cannot be run over a remote connection.", DONE);
                }
            } else if (allCommands.containsKey(commandName)) {
                String[] args = new String[cmds.length - 1];
                System.arraycopy(cmds, 1, args, 0, cmds.length - 1);
                executeCommand(commandName, args, transport);
            } else {
                transport.sendErr("Unknown command: /" + commandName + ". Type /help for available commands.", DONE);
            }
        } else {
            JShellMessage evalMessage = executeStatement(String.join(" ", cmds));
            evalMessage.errs.forEach(line -> transport.send(ReplResponse.withErr(line)));
            evalMessage.outs.forEach(line -> transport.send(ReplResponse.withOut(line)));
            transport.send(ReplResponse.withOut("").done());
        }
        return false;
    }

    @Override
    public void run() {
        try (ZContext ctx = new ZContext();
             ZMQ.Socket server = ctx.createSocket(SocketType.ROUTER);
             ZMQ.Socket completerSock = ctx.createSocket(SocketType.ROUTER)) {
            int port = Env.getInt("repl.port", 0);
            String host = Objects.requireNonNull(Env.getString("repl.host", "localhost"));
            localOnly = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
            if (port == 0) {
                port = server.bindToRandomPort("tcp://" + host);
            } else {
                server.bind("tcp://" + host + ":" + port);
            }
            LOG.info("Listen {}", port);
            replPort.complete(port);
            writePortFile(port);

            // Completer
            int completerPort = Env.getInt("completer.port", 0);
            if (completerPort == 0) {
                completerPort = completerSock.bindToRandomPort("tcp://" + host);
            } else {
                completerSock.bind("tcp://" + host + ":" + completerPort);
            }
            threadPool.submit(new CompletionServer(completerSock, jshell.sourceCodeAnalysis(), allCommands.keySet()));
            LOG.info("Completer start=tcp://{}:{}", host, completerPort);

            // Start registered transport providers
            final int cPort = completerPort;
            TransportContext transportContext = new TransportContext() {
                @Override
                public void dispatch(String message, Transport transport) {
                    String[] cmds = message.trim().split("\\s+");
                    dispatchCommand(cmds, transport, cPort);
                }

                @Override
                public void registerBroadcast(Object key, Transport transport) {
                    ioProxy.register(key, transport);
                }

                @Override
                public void unregisterBroadcast(Object key) {
                    ioProxy.unregister(key);
                }
            };
            transportProviders.forEach(p -> p.start(transportContext));

            while (!Thread.currentThread().isInterrupted()) {
                ZMsg msg = ZMsg.recvMsg(server);
                ZFrame clientAddress = msg.pop();
                ZmqServerTransport transport = ioProxy.listen(server, clientAddress);

                String[] cmds = msg.popString().trim().split("\\s+");
                // Handle ZMQ-specific commands
                if (cmds[0].equals("/disconnect")) {
                    transport.sendOut("disconnected", DONE);
                    ioProxy.unlisten(clientAddress);
                    continue;
                }
                if (dispatchCommand(cmds, transport, completerPort)) {
                    break;
                }
            }
        } catch (Exception e) {
            LOG.error("REPL server error", e);
        } finally {
            deletePortFile();
            transportProviders.forEach(TransportProvider::stop);
            LOG.info("Shutdown REPL server");
            backgroundTasks.values().forEach(task -> task.cancel(true));
            threadPool.shutdownNow();
            jshell.close();
        }

    }

    private volatile @Nullable Integer lastWrittenPort = null;
    private volatile @Nullable Path lastPortFile = null;

    private static Path getPortFile() {
        String override = System.getProperty("enkan.repl.portFile");
        if (override != null && !override.isBlank()) {
            try {
                return Path.of(override);
            } catch (java.nio.file.InvalidPathException e) {
                LOG.warn("Invalid enkan.repl.portFile value '{}', falling back to default: {}", override, e.getMessage());
            }
        }
        return Path.of(System.getProperty("user.home"), ".enkan-repl-port");
    }

    private void writePortFile(int port) {
        Path portFile = getPortFile();
        try {
            Files.writeString(portFile, Integer.toString(port));
            lastWrittenPort = port;
            lastPortFile = portFile;
        } catch (IOException e) {
            LOG.warn("Failed to write port file {}: {}", portFile, e.getMessage());
        }
    }

    private void deletePortFile() {
        Integer port = lastWrittenPort;
        if (port == null) return;
        Path portFile = lastPortFile != null ? lastPortFile : getPortFile();
        try {
            String content = Files.readString(portFile).trim();
            if (Integer.toString(port).equals(content)) {
                Files.deleteIfExists(portFile);
            }
        } catch (IOException e) {
            LOG.warn("Failed to delete port file {}: {}", portFile, e.getMessage());
        }
    }

    private static class JShellMessage {
        private final List<String> outs = new ArrayList<>();
        private final List<String> errs = new ArrayList<>();
    }
}
