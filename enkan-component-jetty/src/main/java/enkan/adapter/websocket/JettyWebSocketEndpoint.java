package enkan.adapter.websocket;

import enkan.web.websocket.WebSocketHandler;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

import java.nio.ByteBuffer;

/**
 * Jetty {@link Session.Listener} that bridges Jetty WebSocket events
 * to Enkan's {@link WebSocketHandler} callbacks.
 *
 * <p>One instance is created per connection by {@link JettyWebSocketCreatorFactory}.
 * Extends {@link Session.Listener.AbstractAutoDemanding} so flow control
 * (calling {@link Session#demand()}) is handled automatically after each message.
 *
 * @author kawasima
 */
class JettyWebSocketEndpoint extends Session.Listener.AbstractAutoDemanding {

    private final String id;
    private final WebSocketHandler handler;
    private JettyWebSocketSession session;

    JettyWebSocketEndpoint(String id, WebSocketHandler handler) {
        this.id = id;
        this.handler = handler;
    }

    @Override
    public void onWebSocketOpen(Session jettySession) {
        super.onWebSocketOpen(jettySession);
        this.session = new JettyWebSocketSession(id, jettySession);
        handler.onOpen(session);
    }

    @Override
    public void onWebSocketText(String message) {
        handler.onMessage(session, message);
    }

    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
        handler.onBinary(session, payload);
        callback.succeed();
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        handler.onClose(session, statusCode, reason);
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        handler.onError(session, cause);
    }
}
