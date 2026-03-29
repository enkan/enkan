package enkan.component.jetty;

import enkan.adapter.JettyAdapter;
import enkan.application.WebApplication;
import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.ComponentLifecycle;
import enkan.component.HealthCheckable;
import enkan.component.HealthStatus;
import enkan.component.WebServerComponent;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.exception.FalteringEnvironmentException;
import enkan.exception.MisconfigurationException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Set a factory of jetty connector.
     *
     * @param  factory A factory of Jetty connector
     */
    public void setServerConnectorFactory(BiFunction<Server, OptionMap, Connector> factory) {
        this.serverConnectorFactory = factory;
    }
}
