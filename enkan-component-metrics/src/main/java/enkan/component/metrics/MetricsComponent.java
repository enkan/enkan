package enkan.component.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.jmx.JmxReporter;
import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;

import java.util.Collections;
import java.util.SortedSet;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Metrics component based on Dropwizard Metrics.
 *
 * @deprecated Use {@code enkan.component.micrometer.MicrometerComponent} instead.
 *             This class will be removed in a future major release.
 * @author kawasima
 */
@Deprecated(forRemoval = true)
public class MetricsComponent extends SystemComponent<MetricsComponent> {
    private String metricName = "enkan";

    private Meter timeoutsMeter;
    private Meter errorsMeter;
    private Counter activeRequests;
    private Timer requestTimer;

    private JmxReporter reporter;
    private final MetricRegistry metricRegistry;

    public MetricsComponent() {
        metricRegistry = new MetricRegistry();
    }

    @Override
    protected ComponentLifecycle<MetricsComponent> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(MetricsComponent component) {
                component.timeoutsMeter = metricRegistry.meter(name(metricName, "timeouts"));
                component.errorsMeter = metricRegistry.meter(name(metricName, "errors"));
                component.activeRequests = metricRegistry.counter(name(metricName, "activeRequests"));
                component.requestTimer = metricRegistry.timer(name(metricName, "requestTimer"));
                component.reporter = JmxReporter.forRegistry(metricRegistry).build();
                component.reporter.start();
            }

            @Override
            public void stop(MetricsComponent component) {
                component.reporter.stop();
                component.reporter = null;

                SortedSet<String> names = Collections.unmodifiableSortedSet(metricRegistry.getNames());
                names.forEach(metricRegistry::remove);

                component.timeoutsMeter = null;
                component.errorsMeter = null;
                component.activeRequests = null;
                component.requestTimer = null;
            }
        };
    }

    public Meter getTimeouts() {
        return timeoutsMeter;
    }

    public Timer getRequestTimer() {
        return requestTimer;
    }

    public Meter getErrors() {
        return errorsMeter;
    }

    public Counter getActiveRequests() {
        return activeRequests;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }
}
