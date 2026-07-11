package enkan.component.opentelemetry;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

/**
 * System component that wraps an OpenTelemetry instance and provides tracer access.
 *
 * <p>By default, uses {@link OpenTelemetry#noop()} which produces no-op spans.
 * Users can supply their own {@code OpenTelemetry} instance (e.g. configured
 * via the OpenTelemetry SDK or obtained from the Java Agent's
 * {@code GlobalOpenTelemetry}) through the constructor or setter.</p>
 *
 * @author kawasima
 */
public class OpenTelemetryComponent extends SystemComponent<OpenTelemetryComponent> {
    private static final String DEFAULT_INSTRUMENTATION_NAME = "enkan";

    private OpenTelemetry openTelemetry;
    private String instrumentationName = DEFAULT_INSTRUMENTATION_NAME;
    private @Nullable Tracer tracer;

    /**
     * Creates a component with the noop OpenTelemetry instance.
     * Spans will be recorded only when an SDK is present at runtime.
     */
    public OpenTelemetryComponent() {
        this(OpenTelemetry.noop());
    }

    /**
     * Creates a component with the given OpenTelemetry instance.
     *
     * @param openTelemetry a configured OpenTelemetry instance
     */
    public OpenTelemetryComponent(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    protected ComponentLifecycle<OpenTelemetryComponent> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(OpenTelemetryComponent component) {
                component.tracer = component.openTelemetry
                        .getTracer(component.instrumentationName);
            }

            @Override
            public void stop(OpenTelemetryComponent component) {
                component.tracer = null;
            }
        };
    }

    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    public @Nullable Tracer getTracer() {
        return tracer;
    }

    public void setInstrumentationName(String instrumentationName) {
        this.instrumentationName = instrumentationName;
    }

    public void setOpenTelemetry(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }
}
