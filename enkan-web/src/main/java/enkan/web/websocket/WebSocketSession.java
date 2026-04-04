package enkan.web.websocket;

import java.nio.ByteBuffer;

/**
 * Represents a WebSocket connection to a single client.
 *
 * <p>Implementations wrap the server-specific WebSocket session
 * (e.g. Jetty's {@code org.eclipse.jetty.websocket.api.Session})
 * and expose a server-agnostic API.
 *
 * @author kawasima
 */
public interface WebSocketSession {

    /**
     * Returns a unique identifier for this session.
     *
     * @return session ID string
     */
    String getId();

    /**
     * Returns {@code true} if this session is currently open.
     *
     * @return {@code true} when the connection is open
     */
    boolean isOpen();

    /**
     * Sends a UTF-8 text message to the client.
     *
     * <p>The call is asynchronous; delivery errors are reported
     * via {@link WebSocketHandler#onError(WebSocketSession, Throwable)}.
     *
     * @param message the text to send
     */
    void sendText(String message);

    /**
     * Sends a binary message to the client.
     *
     * <p>The call is asynchronous; delivery errors are reported
     * via {@link WebSocketHandler#onError(WebSocketSession, Throwable)}.
     *
     * @param data the binary payload
     */
    void sendBinary(ByteBuffer data);

    /**
     * Initiates a normal close of the connection (status 1000).
     */
    void close();

    /**
     * Initiates a close of the connection with the given status code and reason.
     *
     * @param statusCode RFC 6455 status code
     * @param reason     human-readable reason (may be empty)
     */
    void close(int statusCode, String reason);
}
