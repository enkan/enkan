package enkan.system.repl.websocket;

import enkan.system.ReplResponse;
import enkan.system.Transport;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A {@link Transport} implementation that sends/receives over a WebSocket connection.
 * ReplResponse is serialized as a simple JSON object (no external JSON library).
 *
 * @author kawasima
 */
public class WebSocketServerTransport implements Transport {
    private static final Logger LOG = Logger.getLogger(WebSocketServerTransport.class.getName());

    private final WebSocketSession session;
    private final LinkedBlockingQueue<String> incoming = new LinkedBlockingQueue<>();

    public WebSocketServerTransport(WebSocketSession session) {
        this.session = session;
    }

    /**
     * Enqueue an incoming message from the WebSocket client.
     */
    public void enqueue(String message) {
        incoming.offer(message);
    }

    @Override
    public void send(ReplResponse response) {
        try {
            session.sendText(toJson(response));
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to send WebSocket message", e);
        }
    }

    @Override
    public @Nullable String recv(long timeout) {
        try {
            if (timeout < 0) {
                return incoming.take();
            }
            return incoming.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public WebSocketSession getSession() {
        return session;
    }

    /**
     * Serialize a ReplResponse to a JSON string without any external library.
     * Format: {"out":"...","err":"...","status":["DONE","ERROR"]}
     */
    static String toJson(ReplResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');

        boolean needComma = false;

        if (response.getOut() != null) {
            sb.append("\"out\":\"").append(escapeJson(response.getOut())).append('"');
            needComma = true;
        }

        if (response.getErr() != null) {
            if (needComma) sb.append(',');
            sb.append("\"err\":\"").append(escapeJson(response.getErr())).append('"');
            needComma = true;
        }

        if (!response.getStatus().isEmpty()) {
            if (needComma) sb.append(',');
            String statuses = response.getStatus().stream()
                    .map(Enum::name)
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(","));
            sb.append("\"status\":[").append(statuses).append(']');
        }

        sb.append('}');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
