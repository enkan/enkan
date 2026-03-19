package enkan.component.micrometer;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics component.
 *
 * <p>Wraps a {@link MeterRegistry} and exposes HTTP server metrics:
 * request timer, active request gauge, and error counter.</p>
 *
 * <p>By default uses {@link SimpleMeterRegistry}. Pass a custom registry
 * (e.g. {@code PrometheusMeterRegistry}) via the constructor for production use.</p>
 *
 * @author kawasima
 */
public class MicrometerComponent extends SystemComponent<MicrometerComponent> {
    private String metricPrefix = "enkan";

    private final MeterRegistry registry;
    private Timer requestTimer;
    private Counter errorCounter;
    private Gauge activeRequestsGauge;
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public MicrometerComponent() {
        this(new SimpleMeterRegistry());
    }

    public MicrometerComponent(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected ComponentLifecycle<MicrometerComponent> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(MicrometerComponent component) {
                component.requestTimer = Timer.builder(metricPrefix + ".http.server.requests")
                        .description("HTTP server request duration")
                        .register(registry);
                component.errorCounter = Counter.builder(metricPrefix + ".http.server.errors")
                        .description("HTTP server error count")
                        .register(registry);
                component.activeRequestsGauge = Gauge.builder(
                                metricPrefix + ".http.server.active.requests",
                                component.activeRequests, AtomicInteger::get)
                        .description("HTTP server active request count")
                        .register(registry);
            }

            @Override
            public void stop(MicrometerComponent component) {
                if (component.requestTimer != null) {
                    registry.remove(component.requestTimer);
                }
                if (component.errorCounter != null) {
                    registry.remove(component.errorCounter);
                }
                if (component.activeRequestsGauge != null) {
                    registry.remove(component.activeRequestsGauge);
                }
                component.requestTimer = null;
                component.errorCounter = null;
                component.activeRequestsGauge = null;
            }
        };
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public Timer getRequestTimer() {
        return requestTimer;
    }

    public Counter getErrorCounter() {
        return errorCounter;
    }

    public AtomicInteger getActiveRequests() {
        return activeRequests;
    }

    public void setMetricPrefix(String metricPrefix) {
        this.metricPrefix = metricPrefix;
    }

    public String getMetricPrefix() {
        return metricPrefix;
    }
}
