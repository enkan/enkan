package enkan.web.websocket;

import java.nio.ByteBuffer;

/**
 * Callback interface for handling WebSocket lifecycle events.
 *
 * <p>Implement this interface and register it on the server component
 * (e.g. {@code JettyComponent.addWebSocket(path, handler)}) to handle
 * WebSocket connections at the given path.
 *
 * <p>Example — echo server:
 * <pre>{@code
 * WebSocketHandler echo = new WebSocketHandler() {
 *     public void onOpen(WebSocketSession session) {}
 *     public void onMessage(WebSocketSession session, String message) {
 *         session.sendText(message);
 *     }
 *     public void onClose(WebSocketSession session, int code, String reason) {}
 *     public void onError(WebSocketSession session, Throwable cause) {
 *         LOG.error("WebSocket error on {}", session.getId(), cause);
 *     }
 * };
 * }</pre>
 *
 * @author kawasima
 */
public interface WebSocketHandler {

    /**
     * Called when a new WebSocket connection is established.
     *
     * @param session the newly opened session
     */
    void onOpen(WebSocketSession session);

    /**
     * Called when a UTF-8 text message is received from the client.
     *
     * @param session the session that received the message
     * @param message the received text
     */
    void onMessage(WebSocketSession session, String message);

    /**
     * Called when a binary message is received from the client.
     *
     * <p>The default implementation discards binary frames silently.
     * Override to handle binary data.
     *
     * @param session the session that received the data
     * @param data    the binary payload
     */
    default void onBinary(WebSocketSession session, ByteBuffer data) {
        // ignored by default
    }

    /**
     * Called when the connection is closed, either by the client or by the server.
     *
     * @param session    the closed session
     * @param statusCode RFC 6455 close status code
     * @param reason     human-readable close reason (may be empty)
     */
    void onClose(WebSocketSession session, int statusCode, String reason);

    /**
     * Called when an error occurs on the connection.
     *
     * <p>The connection will be closed after this callback returns.
     *
     * @param session the affected session
     * @param cause   the error
     */
    void onError(WebSocketSession session, Throwable cause);
}
