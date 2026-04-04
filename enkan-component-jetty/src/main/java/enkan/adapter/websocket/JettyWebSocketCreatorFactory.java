package enkan.adapter.websocket;

import enkan.web.websocket.WebSocketHandler;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketCreator;

import java.util.Objects;
import java.util.UUID;

/**
 * Factory for creating Jetty {@link JettyWebSocketCreator} instances
 * that delegate to Enkan {@link WebSocketHandler} callbacks.
 *
 * <p>Keeps {@link JettyWebSocketEndpoint} and {@link JettyWebSocketSession}
 * as package-private implementation details.
 *
 * @author kawasima
 */
public final class JettyWebSocketCreatorFactory {

    private JettyWebSocketCreatorFactory() {}

    /**
     * Returns a {@link JettyWebSocketCreator} that creates a new
     * {@link JettyWebSocketEndpoint} per connection, each with a
     * fresh random session ID.
     *
     * @param handler the Enkan handler to receive WebSocket events
     * @return a Jetty creator suitable for
     *         {@code JettyWebSocketServerContainer.addMapping()}
     */
    public static JettyWebSocketCreator forHandler(WebSocketHandler handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        return (req, resp) -> new JettyWebSocketEndpoint(UUID.randomUUID().toString(), handler);
    }
}
