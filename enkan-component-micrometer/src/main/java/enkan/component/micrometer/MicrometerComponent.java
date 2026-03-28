package enkan.component.micrometer;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

    private final @NonNull MeterRegistry registry;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private @Nullable Timer requestTimer;
    private @Nullable Counter errorCounter;
    private @Nullable Gauge activeRequestsGauge;

    public MicrometerComponent() {
        this(new SimpleMeterRegistry());
    }

    public MicrometerComponent(@NonNull MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected ComponentLifecycle<MicrometerComponent> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(MicrometerComponent component) {
                component.registerMetrics();
            }

            @Override
            public void stop(MicrometerComponent component) {
                component.removeMetrics();
            }
        };
    }

    private void registerMetrics() {
        requestTimer = Timer.builder(metricPrefix + ".http.server.requests")
                .description("HTTP server request duration")
                .register(registry);
        errorCounter = Counter.builder(metricPrefix + ".http.server.errors")
                .description("HTTP server error count")
                .register(registry);
        activeRequestsGauge = Gauge.builder(
                        metricPrefix + ".http.server.active.requests",
                        activeRequests, AtomicInteger::get)
                .description("HTTP server active request count")
                .register(registry);
    }

    private void removeMetrics() {
        if (requestTimer != null) registry.remove(requestTimer);
        if (errorCounter != null) registry.remove(errorCounter);
        if (activeRequestsGauge != null) registry.remove(activeRequestsGauge);
        requestTimer = null;
        errorCounter = null;
        activeRequestsGauge = null;
    }

    public @NonNull MeterRegistry getRegistry() {
        return registry;
    }

    public @Nullable Timer getRequestTimer() {
        return requestTimer;
    }

    public @Nullable Counter getErrorCounter() {
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
