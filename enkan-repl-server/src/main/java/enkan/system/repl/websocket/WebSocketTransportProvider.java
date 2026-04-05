package enkan.system.repl.websocket;

import enkan.system.repl.TransportContext;
import enkan.system.repl.TransportProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A {@link TransportProvider} that starts a WebSocket server for browser-based REPL access.
 *
 * @author kawasima
 */
public class WebSocketTransportProvider implements TransportProvider {
    private final int port;
    private WebSocketServer wsServer;

    /**
     * Create a provider with an ephemeral (auto-assigned) port.
     */
    public WebSocketTransportProvider() {
        this(0);
    }

    /**
     * Create a provider with a specific port.
     *
     * @param port the port to listen on, or 0 for ephemeral
     */
    public WebSocketTransportProvider(int port) {
        this.port = port;
    }

    @Override
    public void start(TransportContext context) {
        Map<String, WebSocketServerTransport> wsTransports = new ConcurrentHashMap<>();
        wsServer = new WebSocketServer(port, (session, message) -> {
            WebSocketServerTransport t = wsTransports.computeIfAbsent(session.getId(), id -> {
                WebSocketServerTransport transport = new WebSocketServerTransport(session);
                context.registerBroadcast(id, transport);
                return transport;
            });
            context.dispatch(message, t);
        });
        wsServer.setOnDisconnect(sessionId -> {
            wsTransports.remove(sessionId);
            context.unregisterBroadcast(sessionId);
        });
        Thread wsThread = new Thread(wsServer);
        wsThread.setDaemon(true);
        wsThread.setName("enkan-repl-websocket");
        wsThread.start();
        try {
            wsServer.awaitBound(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.setProperty("enkan.repl.ws.port", String.valueOf(wsServer.getPort()));
    }

    /**
     * Return the actual port the WebSocket server is bound to.
     * Only valid after {@link #start} has been called.
     */
    public int getPort() {
        return wsServer != null ? wsServer.getPort() : port;
    }

    @Override
    public void stop() {
        if (wsServer != null) {
            wsServer.stop();
        }
    }
}
