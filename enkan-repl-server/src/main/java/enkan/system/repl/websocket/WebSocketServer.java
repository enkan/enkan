package enkan.system.repl.websocket;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A zero-dependency WebSocket server built on JDK ServerSocket.
 * Handles HTTP Upgrade handshake and manages client connections.
 *
 * @author kawasima
 */
public class WebSocketServer implements Runnable, Closeable {
    private static final Logger LOG = Logger.getLogger(WebSocketServer.class.getName());
    private static final String WS_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final BiConsumer<WebSocketSession, String> messageHandler;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final CountDownLatch boundLatch = new CountDownLatch(1);
    private volatile ServerSocket serverSocket;
    private volatile boolean stopped;
    private volatile Consumer<String> onDisconnect;

    /**
     * @param port           the port to listen on
     * @param messageHandler callback invoked for each received text message
     */
    public WebSocketServer(int port, BiConsumer<WebSocketSession, String> messageHandler) {
        this.port = port;
        this.messageHandler = messageHandler;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            boundLatch.countDown();
            LOG.info("WebSocket server listening on port " + serverSocket.getLocalPort());

            while (!stopped) {
                Socket client;
                try {
                    client = serverSocket.accept();
                } catch (SocketException e) {
                    if (stopped) break;
                    throw e;
                }

                Thread handler = new Thread(() -> handleClient(client));
                handler.setDaemon(true);
                handler.setName("ws-client-" + client.getRemoteSocketAddress());
                handler.start();
            }
        } catch (IOException e) {
            if (!stopped) {
                LOG.log(Level.SEVERE, "WebSocket server error", e);
            }
        }
    }

    private void handleClient(Socket client) {
        WebSocketSession session = null;
        try {
            // Use a single BufferedInputStream for both handshake and WebSocket frames.
            // BufferedReader must NOT be used here — it reads ahead and steals frame bytes.
            BufferedInputStream bis = new BufferedInputStream(client.getInputStream());
            BufferedOutputStream bos = new BufferedOutputStream(client.getOutputStream());

            if (!performHandshake(bis, bos)) {
                client.close();
                return;
            }

            session = new WebSocketSession(client, bis, bos);
            sessions.put(session.getId(), session);
            LOG.info("WebSocket client connected: " + session.getId());

            LOG.info("WebSocket session ready, waiting for messages...");
            while (!session.isClosed()) {
                String message = session.readMessage();
                if (message == null) {
                    LOG.info("WebSocket readMessage returned null");
                    break;
                }
                LOG.info("WebSocket received: " + message);
                messageHandler.accept(session, message);
            }
        } catch (EOFException e) {
            // client disconnected
        } catch (IOException e) {
            if (!stopped) {
                LOG.log(Level.FINE, "WebSocket client error", e);
            }
        } finally {
            if (session != null) {
                String sessionId = session.getId();
                sessions.remove(sessionId);
                try {
                    session.close();
                } catch (IOException ignored) {
                }
                Consumer<String> handler = onDisconnect;
                if (handler != null) {
                    handler.accept(sessionId);
                }
                LOG.info("WebSocket client disconnected: " + sessionId);
            }
        }
    }

    /**
     * Perform the HTTP Upgrade handshake.
     * Reads headers byte-by-byte from the given InputStream to avoid buffering
     * past the header boundary into WebSocket frame data.
     *
     * @return true if the handshake succeeded
     */
    private boolean performHandshake(InputStream in, OutputStream out) throws IOException {
        // Read headers line-by-line (byte-by-byte to avoid read-ahead)
        String wsKey = null;
        String line;
        boolean firstLine = true;
        while ((line = readLine(in)) != null) {
            if (firstLine) {
                firstLine = false;
                continue; // skip request line (e.g. "GET /repl HTTP/1.1")
            }
            if (line.isEmpty()) break; // end of headers

            if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                wsKey = line.substring("sec-websocket-key:".length()).trim();
            }
        }

        if (wsKey == null) {
            LOG.warning("WebSocket handshake failed: no Sec-WebSocket-Key header found");
            return false;
        }

        String acceptKey = computeAcceptKey(wsKey);
        LOG.info("WebSocket handshake: key=" + wsKey + " accept=" + acceptKey);
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                "\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();

        return true;
    }

    /**
     * Read a single line (terminated by \r\n or \n) byte-by-byte.
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        if (b == -1 && buf.size() == 0) return null;
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static String computeAcceptKey(String wsKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((wsKey + WS_MAGIC_STRING).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    /**
     * Set a callback invoked when a WebSocket client disconnects.
     *
     * @param onDisconnect receives the session ID
     */
    public void setOnDisconnect(Consumer<String> onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    /**
     * Block until the server socket is bound, or the timeout elapses.
     *
     * @param timeout maximum time to wait
     * @param unit    time unit
     * @throws InterruptedException if interrupted while waiting
     */
    public void awaitBound(long timeout, TimeUnit unit) throws InterruptedException {
        boundLatch.await(timeout, unit);
    }

    /**
     * Return the actual port the server is bound to.
     * If the server was created with port 0 (ephemeral), this returns the assigned port
     * once the server is running.
     */
    public int getPort() {
        ServerSocket ss = serverSocket;
        if (ss != null && ss.isBound()) {
            return ss.getLocalPort();
        }
        return port;
    }

    public Map<String, WebSocketSession> getSessions() {
        return sessions;
    }

    public void stop() {
        stopped = true;
        // Close all active sessions
        sessions.values().forEach(session -> {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        });
        sessions.clear();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() throws IOException {
        stop();
    }
}
