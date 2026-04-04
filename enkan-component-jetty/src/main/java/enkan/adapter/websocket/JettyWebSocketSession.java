package enkan.adapter.websocket;

import enkan.web.websocket.WebSocketSession;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;

import java.nio.ByteBuffer;

/**
 * Enkan {@link WebSocketSession} backed by a Jetty {@link Session}.
 *
 * <p>Sends are fire-and-forget (using {@link Callback#NOOP}).
 * Delivery errors are surfaced via
 * {@link enkan.web.websocket.WebSocketHandler#onError(WebSocketSession, Throwable)}.
 *
 * @author kawasima
 */
class JettyWebSocketSession implements WebSocketSession {

    private final String id;
    private final Session jettySession;

    JettyWebSocketSession(String id, Session jettySession) {
        this.id = id;
        this.jettySession = jettySession;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isOpen() {
        return jettySession.isOpen();
    }

    @Override
    public void sendText(String message) {
        jettySession.sendText(message, Callback.NOOP);
    }

    @Override
    public void sendBinary(ByteBuffer data) {
        jettySession.sendBinary(data, Callback.NOOP);
    }

    @Override
    public void close() {
        jettySession.close();
    }

    @Override
    public void close(int statusCode, String reason) {
        jettySession.close(statusCode, reason, Callback.NOOP);
    }
}
