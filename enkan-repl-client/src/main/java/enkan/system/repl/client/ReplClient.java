package enkan.system.repl.client;

import enkan.system.ReplResponse;
import enkan.system.repl.serdes.Fressian;
import enkan.system.repl.serdes.ReplResponseReader;
import enkan.system.repl.serdes.ReplResponseWriter;
import enkan.system.repl.serdes.ResponseStatusReader;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.zeromq.*;
import zmq.ZError;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.Charset;

import static enkan.system.ReplResponse.ResponseStatus.DONE;
import static enkan.system.ReplResponse.ResponseStatus.SHUTDOWN;

/**
 * @author kawasima
 */
public class ReplClient {
    private final ExecutorService clientThread = Executors.newSingleThreadExecutor();
    private ConsoleHandler consoleHandler;

    private static class ConsoleHandler implements Runnable {
        private static final String MONITOR_ADDRESS = "inproc://monitor-";
        private ZContext ctx;
        private ZMQ.Socket socket;
        private ZMQ.Socket rendererSock;
        private ZMQ.Socket completerSock;
        private final LineReader reader;
        private final Fressian fressian;
        private final AtomicBoolean isAvailable = new AtomicBoolean(true);
        private final AtomicBoolean pendingExit = new AtomicBoolean(false);
        private final AtomicBoolean serverDisconnected = new AtomicBoolean(false);

        public ConsoleHandler(LineReader reader) {
            this.reader = reader;
            this.ctx = new ZContext();
            this.fressian = new Fressian();
            fressian.putReadHandler(ReplResponse.class, new ReplResponseReader());
            fressian.putReadHandler(ReplResponse.ResponseStatus.class, new ResponseStatusReader());
            fressian.putWriteHandler(ReplResponse.class, new ReplResponseWriter());
            fressian.putWriteHandler(ReplResponse.ResponseStatus.class, new ReplResponseWriter());
        }

        public void connect(int port) {
            connect("localhost", port);
        }

        public void connect(String host, int port) {
            String monitorAddress = MONITOR_ADDRESS + UUID.randomUUID();
            socket = ctx.createSocket(SocketType.DEALER);
            final AtomicBoolean isSocketClosed = new AtomicBoolean(false);
            if (!socket.monitor(monitorAddress, ZMQ.EVENT_ALL)) {
                System.err.println("Monitoring failed");
            }

            final ZMQ.Socket monitorSocket = ctx.createSocket(SocketType.PAIR);
            monitorSocket.connect(monitorAddress);
            ZThread.fork(ctx, (args, c, pipe) -> {
                int retryCnt = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    // Blocking recv — waits until an event arrives or context is terminated
                    ZEvent event = ZEvent.recv(monitorSocket);
                    if (event == null) {
                        if (monitorSocket.errno() == ZError.ETERM) break;
                        continue;
                    }
                    ZMonitor.Event eventType = event.getEvent();
                    if (eventType == ZMonitor.Event.DISCONNECTED || eventType == ZMonitor.Event.CLOSED) {
                        serverDisconnected.set(true);
                        isSocketClosed.compareAndSet(false, true);
                        close();
                        break;
                    } else if (eventType == ZMonitor.Event.CONNECT_RETRIED) {
                        if (retryCnt++ > 3) {
                            System.err.println("Connection failed");
                            isSocketClosed.compareAndSet(false, true);
                            break;
                        }
                    }
                }
            });

            socket.connect("tcp://" + host + ":" + port);
            final ZMQ.Poller poller = ctx.createPoller(1);
            poller.register(socket, ZMQ.Poller.POLLIN);
            socket.send("/completer");

            ZMsg completerMsg = null;
            while (!Thread.currentThread().isInterrupted()) {
                if (isSocketClosed.get()) {
                    if (socket != null) {
                        socket.close();
                        socket = null;
                    }
                    monitorSocket.close();
                    poller.close();
                    return;
                }

                poller.poll(1000);
                if (poller.pollin(0)) {
                     completerMsg = ZMsg.recvMsg(socket, false);
                     break;
                }
            }
            assert completerMsg != null;
            ReplResponse completerRes = fressian.read(completerMsg.pop().getData(), ReplResponse.class);
            String completerPort = completerRes.getOut();
            if (completerPort != null && completerPort.matches("\\d+")) {
                completerSock = ctx.createSocket(SocketType.DEALER);
                completerSock.connect("tcp://" + host + ":" + Integer.parseInt(completerPort));
                if (reader instanceof org.jline.reader.impl.LineReaderImpl) {
                    RemoteCompleter completer = new RemoteCompleter(completerSock);
                    ((org.jline.reader.impl.LineReaderImpl) reader).setCompleter(completer);
                } else {
                    System.err.println("Reader is not an instance of LineReaderImpl: " + reader.getClass());
                }
            }
            reader.getTerminal().writer().println("Connected to server (port = " + port +")");
            reader.getTerminal().writer().flush();

            rendererSock = ZThread.fork(ctx, (args, c, pipe) -> {
                while (socket != null) {
                    try {
                        ZMsg msg = ZMsg.recvMsg(this.socket);
                        ReplResponse res = fressian.read(msg.pop().getData(), ReplResponse.class);
                        if (res.getOut() != null) {
                            reader.getTerminal().writer().println(res.getOut());
                        } else if (res.getErr() != null) {
                            reader.getTerminal().writer().println(res.getErr());
                        }
                        if (res.getStatus().contains(SHUTDOWN)) {
                            reader.getTerminal().writer().flush();
                            pipe.send("shutdown");
                            break;
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

        @Override
        public void run() {
            while(isAvailable.get()) {
                try {
                    String prompt = pendingExit.get() ? "(Press Ctrl+C again to exit) enkan> " : "enkan> ";
                    String line = reader.readLine(prompt);
                    pendingExit.set(false);
                    if (line == null) continue;
                    line = line.trim();
                    if (line.startsWith("/connect ")) {
                        String[] arguments = line.split("\\s+");
                        if (arguments.length == 2 && arguments[1].matches("\\d+")) {
                            int port = Integer.parseInt(arguments[1]);
                            connect(port);
                        } else if (arguments.length > 2 && arguments[2].matches("\\d+")) {
                            String host = arguments[1];
                            int port = Integer.parseInt(arguments[2]);
                            connect(host, port);
                        } else {
                            reader.getTerminal().writer().println("/connect [host] port");
                        }
                    } else if (line.equals("/exit")) {
                        if (rendererSock != null) {
                            rendererSock.close();
                        }
                        closeQuietly();
                        return;
                    } else {
                        if (this.socket == null) {
                            reader.getTerminal().writer().println("Unconnected to enkan system.");
                        } else {
                            reader.getHistory().save();
                            this.socket.send(line);
                            String serverInstruction = null;
                            while (isAvailable.get() && serverInstruction == null) {
                                serverInstruction = rendererSock.recvStr(500);
                            }
                            if (Objects.equals(serverInstruction, "shutdown") || !isAvailable.get()) {
                                closeQuietly();
                                break;
                            }
                        }
                    }
                } catch (EndOfFileException e) {
                    closeQuietly();
                    return;
                } catch (UserInterruptException e) {
                    if (serverDisconnected.get()) {
                        reader.getTerminal().writer().println("Server disconnected.");
                        reader.getTerminal().writer().flush();
                        return;
                    }
                    if (pendingExit.get()) {
                        closeQuietly();
                        return;
                    }
                    pendingExit.set(true);
                    reader.getTerminal().writer().println("(Press Ctrl+C again to exit, or press Enter to continue)");
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
                } catch (Exception ignore) {
                }
            }
            if (rendererSock != null) {
                try {
                    rendererSock.close();
                    rendererSock = null;
                } catch (Exception ignore) {
                }
            }
            if (socket != null) {
                socket.send("/disconnect", ZMQ.DONTWAIT);
                socket.close();
                socket = null;
            }

            if (ctx != null) {
                ctx.close();
                ctx = null;
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
        consoleHandler.close();
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
            clientThread.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
