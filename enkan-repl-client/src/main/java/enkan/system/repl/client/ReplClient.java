package enkan.system.repl.client;

import enkan.system.ReplResponse;
import enkan.system.SystemCommand;
import enkan.system.repl.command.InitCommand;
import enkan.system.repl.serdes.Fressian;
import enkan.system.repl.serdes.ReplResponseReader;
import enkan.system.repl.serdes.ReplResponseWriter;
import enkan.system.repl.serdes.ResponseStatusReader;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.zeromq.*;
import zmq.ZError;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static enkan.system.ReplResponse.ResponseStatus.DONE;
import static enkan.system.ReplResponse.ResponseStatus.NEED_INPUT;
import static enkan.system.ReplResponse.ResponseStatus.SHUTDOWN;

/**
 * @author kawasima
 */
public class ReplClient {
    private static final String LOCAL_HELP_HEADER = "Client commands (available without server connection):";
    private static final Map<String, String> LOCAL_COMMAND_HELP = createLocalCommandHelp();
    private final ExecutorService clientThread = Executors.newSingleThreadExecutor();
    private ConsoleHandler consoleHandler;

    /** Package-private so tests in the same package can drive {@code connect()}
     *  directly without spinning up the full {@link ReplClient} CLI loop. */
    static class ConsoleHandler implements Runnable {
        private static final String MONITOR_ADDRESS = "inproc://monitor-";
        private ZContext ctx;
        private volatile ZMQ.Socket socket;
        private volatile ZMQ.Socket rendererSock;
        private volatile ZMQ.Socket completerSock;
        private final LineReader reader;
        private final Fressian fressian;
        private final Map<String, SystemCommand> clientLocalCommands = new LinkedHashMap<>();
        private final AtomicBoolean isAvailable = new AtomicBoolean(true);
        private final AtomicBoolean pendingExit = new AtomicBoolean(false);
        private final AtomicBoolean serverDisconnected = new AtomicBoolean(false);
        private volatile int connectedPort = -1;

        public ConsoleHandler(LineReader reader) {
            this.reader = reader;
            this.ctx = new ZContext();
            this.fressian = new Fressian();
            fressian.putReadHandler(ReplResponse.class, new ReplResponseReader());
            fressian.putReadHandler(ReplResponse.ResponseStatus.class, new ResponseStatusReader());
            fressian.putWriteHandler(ReplResponse.class, new ReplResponseWriter());
            fressian.putWriteHandler(ReplResponse.ResponseStatus.class, new ReplResponseWriter());
            clientLocalCommands.put("init", new InitCommand());
        }

        /** Test accessor: true while the REPL run loop should keep going. */
        boolean isAvailable() {
            return isAvailable.get();
        }

        /** Test accessor: the currently-connected server port, or -1 when disconnected. */
        int getConnectedPort() {
            return connectedPort;
        }

        public void connect(int port) {
            connect("localhost", port);
        }

        /** Maximum time to wait for the {@code /completer} handshake reply
         *  before declaring the connection attempt failed. */
        private static final long CONNECT_TIMEOUT_MS = 3_000;

        public void connect(String host, int port) {
            if (!ReplClient.isValidHost(host)) {
                printErr(reader, "Invalid host: " + host);
                reader.getTerminal().writer().flush();
                return;
            }
            // Guard against a race where closeSockets() nulls ctx between here and
            // createSocket(). If the REPL is already shutting down, skip the attempt.
            if (!isAvailable.get()) return;
            // Build all per-attempt state on the stack so a failure leaves the
            // existing connection (and the REPL itself) untouched. Only after
            // the completer handshake succeeds do we publish the new sockets to
            // instance fields.
            final String monitorAddress = MONITOR_ADDRESS + UUID.randomUUID();
            final ZMQ.Socket newSocket = ctx.createSocket(SocketType.DEALER);
            final AtomicBoolean attemptFailed = new AtomicBoolean(false);

            if (!newSocket.monitor(monitorAddress, ZMQ.EVENT_ALL)) {
                printErr(reader, "Failed to install ZMQ monitor for /connect " + host + ":" + port);
                newSocket.close();
                return;
            }

            final ZMQ.Socket monitorSocket = ctx.createSocket(SocketType.PAIR);
            monitorSocket.connect(monitorAddress);
            // The monitor has two phases:
            //   Phase 1 (handshakeSeen=false): we're still trying to bring the
            //     connection up. DISCONNECTED / CLOSED / repeated CONNECT_RETRIED
            //     means the attempt failed; signal via attemptFailed and exit
            //     so connect() can clean up the per-attempt sockets.
            //   Phase 2 (handshakeSeen=true): we already saw CONNECTED. From
            //     here on, DISCONNECTED means the server we were talking to
            //     went away — switch to the legacy behaviour of closing the
            //     whole REPL with the "Server disconnected." message.
            //
            // Ownership of monitorSocket: exactly one thread must close it.
            //   - On the failure path, connect() signals the monitor thread to
            //     skip its close (monitorSocketOwned.set(false)) and then closes
            //     the socket itself AFTER the signal, giving the monitor thread a
            //     chance to see ETERM and exit its recv loop cleanly.
            //   - On the success path, the monitor thread is the sole owner and
            //     must close it in its finally block.
            final AtomicBoolean monitorSocketOwned = new AtomicBoolean(true); // true = monitor thread owns it
            final AtomicBoolean handshakeSeen = new AtomicBoolean(false);
            // Use ZThread.start (IDetachedRunnable) instead of ZThread.fork so no
            // parent-side PAIR pipe socket is allocated — the monitor thread does not
            // use the pipe at all, and the socket returned by fork() would be leaked.
            ZThread.start(args -> {
                int retryCnt = 0;
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        ZEvent event = ZEvent.recv(monitorSocket);
                        if (event == null) {
                            if (monitorSocket.errno() == ZError.ETERM) break;
                            continue;
                        }
                        ZMonitor.Event eventType = event.getEvent();
                        if (eventType == ZMonitor.Event.CONNECTED) {
                            handshakeSeen.set(true);
                            retryCnt = 0;
                            continue;
                        }
                        if (eventType == ZMonitor.Event.CONNECT_RETRIED) {
                            if (!handshakeSeen.get() && retryCnt++ > 3) {
                                attemptFailed.set(true);
                                return;
                            }
                            continue;
                        }
                        if (eventType == ZMonitor.Event.DISCONNECTED
                                || eventType == ZMonitor.Event.CLOSED) {
                            if (!handshakeSeen.get()) {
                                // Connection never came up — abort the attempt.
                                attemptFailed.set(true);
                                return;
                            }
                            // Phase 2: we were connected and the server went away.
                            // Match the legacy behaviour and shut the REPL down.
                            serverDisconnected.set(true);
                            close();
                            return;
                        }
                    }
                } finally {
                    // Only close if this thread still owns the socket. On the failure
                    // path, connect() takes ownership (monitorSocketOwned=false) and
                    // closes it itself to avoid a double-close race.
                    if (monitorSocketOwned.get()) {
                        try { monitorSocket.close(); } catch (Throwable ignore) { }
                    }
                }
            });

            newSocket.connect("tcp://" + host + ":" + port);
            final ZMQ.Poller poller = ctx.createPoller(1);
            poller.register(newSocket, ZMQ.Poller.POLLIN);
            newSocket.send("/completer");

            // Poll for the completer reply, bounded by CONNECT_TIMEOUT_MS so we
            // never wait forever on a non-listening port.
            ZMsg completerMsg = null;
            long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
            while (!Thread.currentThread().isInterrupted()) {
                if (attemptFailed.get()) break;
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    attemptFailed.set(true);
                    break;
                }
                poller.poll(Math.min(remaining, 250));
                if (poller.pollin(0)) {
                    completerMsg = ZMsg.recvMsg(newSocket, false);
                    if (completerMsg != null) break;
                }
            }

            if (completerMsg == null || attemptFailed.get()) {
                // Connection failed: tear down the per-attempt sockets and
                // leave the REPL exactly as it was. Print a clearly visible
                // error so the user understands why nothing happened.
                printErr(reader, "Failed to connect to " + host + ":" + port
                        + " (no response within " + (CONNECT_TIMEOUT_MS / 1000) + "s — is the server running?)");
                reader.getTerminal().writer().flush();
                try { poller.close(); } catch (Throwable ignore) { }
                // Transfer ownership before closing so the monitor thread's finally
                // block does not attempt a concurrent double-close.
                monitorSocketOwned.set(false);
                try { monitorSocket.close(); } catch (Throwable ignore) { }
                try { newSocket.close(); } catch (Throwable ignore) { }
                return;
            }

            // ----- Connection accepted: commit the new sockets to instance state. -----
            // The poller was only needed for the handshake — close it now that we have
            // a reply. The monitor socket stays open: the forked monitor thread owns it
            // for phase-2 disconnect detection and will close it when it exits.
            try { poller.close(); } catch (Throwable ignore) { }

            ZFrame completerFrame = completerMsg.pop();
            byte[] completerData = completerFrame != null ? completerFrame.getData() : null;
            if (completerData == null) {
                printErr(reader, "Failed to connect to " + host + ":" + port + " (empty completer reply)");
                reader.getTerminal().writer().flush();
                try { newSocket.close(); } catch (Throwable ignore) { }
                return;
            }
            ReplResponse completerRes = fressian.read(completerData, ReplResponse.class);
            String completerPort = completerRes.getOut();
            ZMQ.Socket newCompleterSock = null;
            if (completerPort != null && completerPort.matches("\\d+")) {
                newCompleterSock = ctx.createSocket(SocketType.DEALER);
                newCompleterSock.connect("tcp://" + host + ":" + Integer.parseInt(completerPort));
                if (reader instanceof org.jline.reader.impl.LineReaderImpl impl) {
                    RemoteCompleter completer = new RemoteCompleter(newCompleterSock);
                    impl.setCompleter(new AggregateCompleter(new StringsCompleter(localCommandNames()), completer));
                } else {
                    System.err.println("Reader is not an instance of LineReaderImpl: " + reader.getClass());
                }
            }

            // If we were already connected to a previous server, close the old
            // sockets cleanly before swapping in the new ones.
            closePreviousConnection();

            // Reset disconnect flag before publishing the new connection so that
            // the run loop does not immediately treat the next Ctrl+C as a server
            // disconnect exit (serverDisconnected may be true from a prior session).
            serverDisconnected.set(false);
            this.socket = newSocket;
            this.completerSock = newCompleterSock;
            this.connectedPort = port;
            printInfo(reader, "Connected to server (port = " + port + ")");
            reader.getTerminal().writer().flush();

            rendererSock = ZThread.fork(ctx, (args, c, pipe) -> {
                while (socket != null) {
                    try {
                        ZMsg msg = ZMsg.recvMsg(this.socket);
                        ReplResponse res = fressian.read(msg.pop().getData(), ReplResponse.class);
                        boolean needsInput = res.getStatus().contains(NEED_INPUT);
                        if (res.getOut() != null) {
                            // NEED_INPUT messages are prompts — keep the cursor on the same line.
                            if (needsInput) {
                                reader.getTerminal().writer().print(res.getOut());
                            } else {
                                reader.getTerminal().writer().println(res.getOut());
                            }
                        } else if (res.getErr() != null) {
                            printErr(reader, res.getErr());
                        }
                        if (res.getStatus().contains(SHUTDOWN)) {
                            reader.getTerminal().writer().flush();
                            pipe.send("shutdown");
                            break;
                        } else if (needsInput) {
                            // Unblock the main loop so the user can type a response.
                            pipe.send("need-input");
                        } else if (res.getStatus().contains(DONE)) {
                            pipe.send("done");
                        }
                        reader.getTerminal().writer().flush();
                    } catch (ZMQException e) {
                        if (e.getErrorCode() == ZError.ETERM) {
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        /**
         * Closes whatever sockets the previous successful {@code /connect} left
         * in instance fields, without touching {@link #isAvailable} or the
         * shared {@link #ctx}. Called from the success path of {@link #connect}
         * just before swapping in the new sockets.
         */
        private void closePreviousConnection() {
            if (this.completerSock != null) {
                try { this.completerSock.close(); } catch (Throwable ignore) { }
                this.completerSock = null;
            }
            if (this.rendererSock != null) {
                try { this.rendererSock.close(); } catch (Throwable ignore) { }
                this.rendererSock = null;
            }
            if (this.socket != null) {
                // Null out the field before closing so the renderer thread's
                // `while (socket != null)` guard sees null on its next iteration
                // and exits cleanly rather than calling recvMsg on a closed socket.
                ZMQ.Socket oldSocket = this.socket;
                this.socket = null;
                try { oldSocket.send("/disconnect", ZMQ.DONTWAIT); } catch (Throwable ignore) { }
                try { oldSocket.close(); } catch (Throwable ignore) { }
            }
        }

        private String buildPrompt() {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            if (connectedPort > 0) {
                sb.append("enkan", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold());
                sb.append("(" + connectedPort + ")", AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
                sb.append("❯ ", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold());
            } else {
                sb.append("enkan", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold());
                sb.append("✗", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold());
                sb.append(" ❯ ", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold());
            }
            return sb.toAnsi(reader.getTerminal());
        }

        private static void printErr(LineReader reader, String message) {
            String colored = new AttributedString(message,
                    AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                    .toAnsi(reader.getTerminal());
            reader.getTerminal().writer().println(colored);
        }

        private static void printInfo(LineReader reader, String message) {
            String colored = new AttributedString(message,
                    AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                    .toAnsi(reader.getTerminal());
            reader.getTerminal().writer().println(colored);
        }

        @Override
        public void run() {
            while(isAvailable.get()) {
                try {
                    String line = reader.readLine(buildPrompt());
                    pendingExit.set(false);
                    if (line == null) continue;
                    line = line.trim();
                    switch (line) {
                        case String s when s.startsWith("/connect ") -> {
                            String[] arguments = s.split("\\s+");
                            if (arguments.length == 2 && arguments[1].matches("\\d+")) {
                                connect(Integer.parseInt(arguments[1]));
                            } else if (arguments.length > 2 && arguments[2].matches("\\d+")) {
                                String host = arguments[1];
                                if (!ReplClient.isValidHost(host)) {
                                    reader.getTerminal().writer().println("Invalid host: " + host);
                                } else {
                                    connect(host, Integer.parseInt(arguments[2]));
                                }
                            } else {
                                reader.getTerminal().writer().println("/connect [host] port");
                            }
                        }
                        case "/exit" -> {
                            if (rendererSock != null) rendererSock.close();
                            closeQuietly();
                            return;
                        }
                        case String s when s.startsWith("/help") -> {
                            reader.getTerminal().writer().println(formatLocalHelp(s));
                            reader.getTerminal().writer().flush();
                        }
                        case String s when s.startsWith("/") -> {
                            String cmdName = s.substring(1).split("\\s+")[0];
                            SystemCommand localCmd = clientLocalCommands.get(cmdName);
                            if (localCmd != null) {
                                try (JLineTransport t = new JLineTransport(reader)) {
                                    t.setConnectCallback(this::connect);
                                    localCmd.execute(null, t);
                                }
                            } else if (!sendToServer(line)) {
                                break;
                            }
                        }
                        default -> {
                            if (!sendToServer(line)) break;
                        }
                    }
                } catch (EndOfFileException e) {
                    closeQuietly();
                    return;
                } catch (UserInterruptException e) {
                    if (serverDisconnected.get()) {
                        printErr(reader, "Server disconnected.");
                        reader.getTerminal().writer().flush();
                        return;
                    }
                    if (pendingExit.get()) {
                        closeQuietly();
                        return;
                    }
                    pendingExit.set(true);
                    printInfo(reader, "(Press Ctrl+C again to exit, or press Enter to continue)");
                    reader.getTerminal().writer().flush();
                } catch (ZMQException e) {
                    if (e.getErrorCode() == ZError.ETERM) {
                        break;
                    }
                } catch (ZError.CtxTerminatedException e) {
                    System.err.println("disconnected");
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        /**
         * Sends {@code line} to the connected server and waits for the renderer
         * to signal completion. Returns {@code false} when the server signals
         * shutdown (so the caller can break the run loop), {@code true} otherwise.
         * Prints an error and returns {@code true} when not connected.
         */
        private boolean sendToServer(String line) throws IOException {
            if (this.socket == null) {
                reader.getTerminal().writer().println("Unconnected to enkan system.");
                return true;
            }
            reader.getHistory().save();
            this.socket.send(line);
            String serverInstruction = null;
            while (isAvailable.get() && serverInstruction == null) {
                ZMQ.Socket rs = rendererSock;
                if (rs == null) break;
                serverInstruction = rs.recvStr(500);
            }
            if (Objects.equals(serverInstruction, "shutdown") || !isAvailable.get()) {
                closeQuietly();
                return false;
            }
            return true;
        }

        /**
         * Closes without raising INT signal. Used when called from within
         * the run() loop where readLine() is not blocking.
         */
        private void closeQuietly() {
            if (!isAvailable.compareAndSet(true, false)) {
                return;
            }
            closeSockets();
        }

        public void close() {
            if (!isAvailable.compareAndSet(true, false)) {
                return; // already closed
            }
            // Wake up readLine() by closing the terminal input
            try {
                reader.getTerminal().close();
            } catch (Exception ignore) {
            }
            closeSockets();
        }

        private void closeSockets() {
            if (completerSock != null) {
                try {
                    completerSock.close();
                    completerSock = null;
                } catch (Throwable ignore) {
                }
            }
            if (rendererSock != null) {
                try {
                    rendererSock.close();
                    rendererSock = null;
                } catch (Throwable ignore) {
                }
            }
            if (socket != null) {
                // Null out before closing so concurrent reads on the field see null
                // and stop trying to use the socket handle.
                ZMQ.Socket s = socket;
                socket = null;
                try { s.send("/disconnect", ZMQ.DONTWAIT); } catch (Throwable ignore) { }
                try { s.close(); } catch (Throwable ignore) { }
            }

            if (ctx != null) {
                ZContext c = ctx;
                ctx = null;
                try { c.close(); } catch (Throwable ignore) { }
            }
        }
    }

    public void start(String initialHost, int initialPort) {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .encoding(Charset.defaultCharset())
                    .build();

            DefaultParser parser = new DefaultParser();
            parser.setEscapeChars(null);

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(parser)
                    .completer(new StringsCompleter(localCommandNames()))
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .option(LineReader.Option.COMPLETE_IN_WORD, true)
                    .option(LineReader.Option.AUTO_MENU, true)
                    .build();

            reader.setVariable(LineReader.SECONDARY_PROMPT_PATTERN, "");
            reader.setVariable(LineReader.HISTORY_FILE, new File(System.getProperty("user.home"), ".enkan_history"));

            consoleHandler = new ConsoleHandler(reader);
            if (initialPort > 0) {
                consoleHandler.connect(initialHost, initialPort);
            }
            clientThread.execute(consoleHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start(int initialPort) {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .encoding(Charset.defaultCharset())
                    .build();

            DefaultParser parser = new DefaultParser();
            parser.setEscapeChars(null);

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(parser)
                    .completer(new StringsCompleter(localCommandNames()))
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .option(LineReader.Option.COMPLETE_IN_WORD, true)
                    .option(LineReader.Option.AUTO_MENU, true)
                    .build();

            reader.setVariable(LineReader.SECONDARY_PROMPT_PATTERN, "");
            reader.setVariable(LineReader.HISTORY_FILE, new File(System.getProperty("user.home"), ".enkan_history"));

            consoleHandler = new ConsoleHandler(reader);
            if (initialPort > 0) {
                consoleHandler.connect(initialPort);
            }
            clientThread.execute(consoleHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        start(-1);
    }

    public void close() {
        if (consoleHandler != null) consoleHandler.close();
        try {
            clientThread.shutdown();
            if (!clientThread.awaitTermination(1L, TimeUnit.SECONDS)) {
                clientThread.shutdownNow();
            }
        } catch (InterruptedException ex) {
            clientThread.shutdownNow();
        }
    }

    static int readPortFile() {
        Path portFile = resolvePortFile();
        try {
            String content = Files.readString(portFile).trim();
            int port = Integer.parseInt(content);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return -1;
    }

    private static Path resolvePortFile() {
        String override = System.getProperty("enkan.repl.portFile");
        if (override != null && !override.isBlank()) {
            try {
                return Path.of(override);
            } catch (java.nio.file.InvalidPathException ignored) {
            }
        }
        return Path.of(System.getProperty("user.home"), ".enkan-repl-port");
    }

    /** Returns true if {@code host} is a syntactically valid hostname or IP address. */
    static boolean isValidHost(String host) {
        return host != null && host.matches("[a-zA-Z0-9][a-zA-Z0-9.\\-]*");
    }

    static String[] localCommandNames() {
        return LOCAL_COMMAND_HELP.keySet().stream()
                .map(name -> "/" + name)
                .toArray(String[]::new);
    }

    static String formatLocalHelp(String line) {
        String[] arguments = line.trim().split("\\s+");
        if (arguments.length >= 2) {
            String command = arguments[1].startsWith("/") ? arguments[1].substring(1) : arguments[1];
            String detail = LOCAL_COMMAND_HELP.get(command);
            if (detail != null) {
                return "/" + command + " " + detail;
            } else {
                return "Unknown client command: " + arguments[1];
            }
        }

        StringBuilder help = new StringBuilder(LOCAL_HELP_HEADER).append('\n');
        for (Map.Entry<String, String> entry : LOCAL_COMMAND_HELP.entrySet()) {
            help.append('/').append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
        }
        return help.toString().trim();
    }

    private static Map<String, String> createLocalCommandHelp() {
        Map<String, String> commands = new LinkedHashMap<>();
        commands.put("connect", "[host] port  Connect to enkan system.");
        commands.put("help", "[command]    Show client help.");
        commands.put("init", "            Generate a new Enkan project using AI.");
        commands.put("exit", "            Exit this client.");
        return commands;
    }

    public static void main(String[] args) {
        final ReplClient client = new ReplClient();
        Runtime.getRuntime().addShutdownHook(new Thread(client::close));
        if (args.length == 1 && args[0].matches("\\d+")) {
            client.start(Integer.parseInt(args[0]));
        } else if (args.length == 2 && args[1].matches("\\d+")) {
            client.start(args[0], Integer.parseInt(args[1]));
        } else if (args.length == 0) {
            int port = readPortFile();
            if (port > 0) {
                client.start(port);
            } else {
                client.start();
            }
        } else {
            client.start();
        }
        // Block until the console handler finishes, then shut down the executor
        // so the JVM can exit. Without this, the non-daemon executor thread
        // keeps the JVM alive after Ctrl+C because JLine consumes SIGINT
        // as UserInterruptException and it never reaches the shutdown hook.
        client.awaitAndShutdown();
    }

    private void awaitAndShutdown() {
        clientThread.shutdown();
        try {
            if (!clientThread.awaitTermination(30, TimeUnit.SECONDS)) {
                clientThread.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientThread.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
