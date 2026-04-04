package enkan.component.jetty;

import enkan.adapter.JettyAdapter;
import enkan.web.application.WebApplication;
import enkan.web.util.DigestFieldsUtils;
import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.ComponentLifecycle;
import enkan.component.HealthCheckable;
import enkan.component.HealthStatus;
import enkan.component.WebServerComponent;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.FalteringEnvironmentException;
import enkan.exception.MisconfigurationException;
import enkan.web.websocket.WebSocketHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author kawasima
 */
public class JettyComponent extends WebServerComponent<JettyComponent> implements HealthCheckable {
    private static final Logger LOG = LoggerFactory.getLogger(JettyComponent.class);

    private Server server;
    private BiFunction<Server, OptionMap, Connector> serverConnectorFactory;
    private boolean virtualThreads = true;
    private volatile boolean stopping = false;
    private final Map<String, WebSocketHandler> wsHandlers = new LinkedHashMap<>();
    private String digestAlgorithm = null;

    @Override
    protected ComponentLifecycle<JettyComponent> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(JettyComponent component) {
                @SuppressWarnings("unchecked")
                ApplicationComponent<HttpRequest, HttpResponse> app = getDependency(ApplicationComponent.class);
                if (server == null) {
                    OptionMap options = buildOptionMap();
                    if (serverConnectorFactory != null) options.put("serverConnectorFactory", serverConnectorFactory);
                    options.put("join?", false);
                    options.put("virtualThreads?", virtualThreads);
                    if (!(app.getApplication() instanceof WebApplication webApp)) {
                        throw new MisconfigurationException("web.APPLICATION_NOT_WEB");
                    }
                    options.put("wsHandlers", Map.copyOf(wsHandlers));
                    if (digestAlgorithm != null) options.put("digestAlgorithm", digestAlgorithm);
                    server = new JettyAdapter().runJetty(webApp, options);
                }
            }

            @Override
            public void stop(JettyComponent component) {
                if (server != null) {
                    try {
                        // Phase 1: Mark as stopping and wait for LB/K8s Endpoints propagation
                        stopping = true;
                        long preStopDelay = getPreStopDelay();
                        if (preStopDelay > 0) {
                            LOG.info("Graceful shutdown: waiting {}ms for load balancer propagation", preStopDelay);
                            try {
                                Thread.sleep(preStopDelay);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                LOG.warn("Pre-stop delay interrupted, proceeding to drain");
                            }
                        }
                        // Phase 2+3: Jetty handles drain + timeout via stopTimeout
                        LOG.info("Graceful shutdown: draining in-flight requests (timeout={}ms)", getStopTimeout());
                        server.stop();
                        server.join();
                    } catch (Exception ex) {
                        throw new FalteringEnvironmentException(ex);
                    } finally {
                        server = null;
                        stopping = false;
                    }
                }
            }
        };
    }

    @Override
    public HealthStatus health() {
        if (stopping) {
            return HealthStatus.STOPPING;
        }
        if (server == null) {
            return HealthStatus.DOWN;
        }
        if (server.isStarting()) {
            return HealthStatus.STARTING;
        }
        return server.isRunning() ? HealthStatus.UP : HealthStatus.DOWN;
    }

    public boolean isVirtualThreads() {
        return virtualThreads;
    }

    public void setVirtualThreads(boolean virtualThreads) {
        this.virtualThreads = virtualThreads;
    }

    /**
     * Registers a WebSocket handler at the given path.
     *
     * <p>Must be called before the component is started.
     * Multiple paths can be registered by chaining calls.
     *
     * <p>Example:
     * <pre>{@code
     * new JettyComponent()
     *     .addWebSocket("/ws/chat", new ChatHandler())
     *     .addWebSocket("/ws/notify", new NotifyHandler())
     * }</pre>
     *
     * @param path    the URL path to serve WebSocket connections on; follows Jetty path-mapping
     *                syntax — exact match (e.g. {@code /ws/echo}) or wildcard prefix
     *                (e.g. {@code /ws/*})
     * @param handler the handler for WebSocket lifecycle events
     * @return {@code this} for chaining
     */
    public JettyComponent addWebSocket(String path, WebSocketHandler handler) {
        if (path == null || path.isBlank()) {
            throw new MisconfigurationException("web.WEBSOCKET_PATH_REQUIRED", path);
        }
        if (handler == null) {
            throw new MisconfigurationException("web.WEBSOCKET_HANDLER_REQUIRED", path);
        }
        if (server != null) {
            throw new MisconfigurationException("web.WEBSOCKET_MUST_REGISTER_BEFORE_START", path);
        }
        wsHandlers.put(path, handler);
        return this;
    }

    /**
     * Enables RFC 9530 Digest Fields generation on responses.
     *
     * <p>Must be called before the component is started.
     * Registers a {@code DigestFilter} (inside the servlet context, for {@code Repr-Digest})
     * and, when compression is also enabled, a {@code ContentDigestHandler}
     * (outside {@code CompressionHandler}, for {@code Content-Digest}).
     *
     * <p>Example:
     * <pre>{@code
     * new JettyComponent()
     *     .enableDigestFields("sha-256")
     * }</pre>
     *
     * @param algorithm the default SF algorithm name ({@code "sha-256"} or {@code "sha-512"})
     * @return {@code this} for chaining
     * @throws MisconfigurationException if the algorithm is not supported
     */
    public JettyComponent enableDigestFields(String algorithm) {
        if (!DigestFieldsUtils.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            throw new MisconfigurationException("web.DIGEST_ALGORITHM_UNSUPPORTED", algorithm);
        }
        if (server != null) {
            throw new MisconfigurationException("jetty.DIGEST_FIELDS_MUST_ENABLE_BEFORE_START", algorithm);
        }
        this.digestAlgorithm = algorithm;
        return this;
    }

    /**
     * Set a factory of jetty connector.
     *
     * @param  factory A factory of Jetty connector
     */
    public void setServerConnectorFactory(BiFunction<Server, OptionMap, Connector> factory) {
        this.serverConnectorFactory = factory;
    }
}
