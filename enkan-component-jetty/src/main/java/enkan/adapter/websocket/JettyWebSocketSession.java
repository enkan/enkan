package enkan.adapter.websocket;

import enkan.web.websocket.WebSocketHandler;
import enkan.web.websocket.WebSocketSession;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

import java.nio.ByteBuffer;

/**
 * Enkan {@link WebSocketSession} backed by a Jetty {@link Session}.
 *
 * <p>Sends are asynchronous. Delivery failures are routed to
 * {@link WebSocketHandler#onError(WebSocketSession, Throwable)} so the
 * application receives a consistent error signal.
 *
 * @author kawasima
 */
class JettyWebSocketSession implements WebSocketSession {

    private final String id;
    private final Session jettySession;
    private final WebSocketHandler handler;

    JettyWebSocketSession(String id, Session jettySession, WebSocketHandler handler) {
        this.id = id;
        this.jettySession = jettySession;
        this.handler = handler;
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
        jettySession.sendText(message, Callback.from(() -> {}, cause -> handler.onError(this, cause)));
    }

    @Override
    public void sendBinary(ByteBuffer data) {
        ByteBuffer copy = ByteBuffer.allocate(data.remaining());
        copy.put(data.duplicate());
        copy.flip();
        jettySession.sendBinary(copy, Callback.from(() -> {}, cause -> handler.onError(this, cause)));
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
