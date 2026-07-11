package enkan.middleware.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.component.metrics.MetricsComponent;
import org.jspecify.annotations.Nullable;

import jakarta.inject.Inject;
import java.util.Objects;

/**
 * @deprecated Use {@code enkan.middleware.micrometer.MicrometerMiddleware} instead.
 *             This class will be removed in a future major release.
 * @author kawasima
 */
@Deprecated(forRemoval = true)
@enkan.annotation.Middleware(name = "metrics")
public class MetricsMiddleware<REQ, RES> implements DecoratorMiddleware<REQ, RES> {
    @Inject
    private MetricsComponent metrics;

    @Override
    public <NNREQ, NNRES> @Nullable RES handle(REQ req, MiddlewareChain<REQ, RES, NNREQ, NNRES> chain) {
        Timer.Context context = Objects.requireNonNull(metrics.getRequestTimer(),
                "MetricsComponent has not been started").time();
        Counter activeRequests = Objects.requireNonNull(metrics.getActiveRequests(),
                "MetricsComponent has not been started");
        activeRequests.inc();

        try {
            return chain.next(req);
        } catch (Exception ex) {
            Objects.requireNonNull(metrics.getErrors()).mark();
            throw ex;
        } finally {
            activeRequests.dec();
            context.stop();
        }
    }
}
