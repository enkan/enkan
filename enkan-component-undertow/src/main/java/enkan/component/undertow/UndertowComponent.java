package enkan.component.undertow;

import enkan.web.application.WebApplication;
import enkan.web.util.DigestFieldsUtils;
import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.ComponentLifecycle;
import enkan.component.HealthCheckable;
import enkan.component.HealthStatus;
import enkan.component.WebServerComponent;
import enkan.exception.MisconfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author kawasima
 */
public class UndertowComponent extends WebServerComponent<UndertowComponent> implements HealthCheckable {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowComponent.class);

    private UndertowAdapter.UndertowServer server;
    private volatile boolean starting = false;
    private volatile boolean stopping = false;
    private String digestAlgorithm = null;

    @Override
    protected ComponentLifecycle<UndertowComponent> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(UndertowComponent component) {
                ApplicationComponent<?, ?> app = getDependency(ApplicationComponent.class);
                if (server == null) {
                    starting = true;
                    try {
                        OptionMap options = buildOptionMap();
                        if (!(app.getApplication() instanceof WebApplication webApp)) {
                            throw new MisconfigurationException("web.APPLICATION_NOT_WEB");
                        }
                        if (digestAlgorithm != null) options.put("digestAlgorithm", digestAlgorithm);
                        server = new UndertowAdapter().runUndertow(webApp, options);
                    } finally {
                        starting = false;
                    }
                }
            }

            @Override
            public void stop(UndertowComponent component) {
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
                        // Phase 2: Stop accepting new requests and wait for in-flight requests
                        LOG.info("Graceful shutdown: draining in-flight requests (timeout={}ms)", getStopTimeout());
                        server.shutdownHandler().shutdown();
                        try {
                            server.shutdownHandler().awaitShutdown(getStopTimeout());
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            LOG.warn("Drain interrupted, forcing stop");
                        }
                        // Phase 3: Stop the server
                        server.undertow().stop();
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
        if (starting) {
            return HealthStatus.STARTING;
        }
        return server != null ? HealthStatus.UP : HealthStatus.DOWN;
    }

    /**
     * Enables RFC 9530 Digest Fields generation on responses.
     *
     * <p>Must be called before the component is started.
     * Registers a {@code DigestConduit} (inside {@code appHandler}, for {@code Repr-Digest})
     * and, when compression is also enabled, a {@code DigestOuterHandler}
     * (outside {@code EncodingHandler}, for {@code Content-Digest}).
     *
     * <p>Example:
     * <pre>{@code
     * new UndertowComponent()
     *     .enableDigestFields("sha-256")
     * }</pre>
     *
     * @param algorithm the default SF algorithm name ({@code "sha-256"} or {@code "sha-512"})
     * @return {@code this} for chaining
     * @throws MisconfigurationException if the algorithm is not supported
     */
    public UndertowComponent enableDigestFields(String algorithm) {
        if (!DigestFieldsUtils.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            throw new MisconfigurationException("web.DIGEST_ALGORITHM_UNSUPPORTED", algorithm);
        }
        if (server != null) {
            throw new MisconfigurationException("undertow.DIGEST_FIELDS_MUST_ENABLE_BEFORE_START", algorithm);
        }
        this.digestAlgorithm = algorithm;
        return this;
    }

    @Override
    public String toString() {
        return """
                #UndertowComponent {
                  "host": "%s",
                  "port": "%s",
                  "dependencies": "%s"
                }""".stripLeading().formatted(getHost(), getPort(), dependenciesToString());
    }
}
