package enkan.middleware.micrometer;

import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.component.micrometer.MicrometerComponent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import jakarta.inject.Inject;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Middleware that records HTTP request metrics via Micrometer.
 *
 * <p>Tracks request duration, active request count, and error count.</p>
 *
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "micrometer")
public class MicrometerMiddleware<REQ, RES> implements DecoratorMiddleware<REQ, RES> {
    @Inject
    private MicrometerComponent micrometer;

    @Override
    public <NNREQ, NNRES> RES handle(REQ req, MiddlewareChain<REQ, RES, NNREQ, NNRES> chain) {
        Timer requestTimer = Objects.requireNonNull(micrometer.getRequestTimer(),
                "MicrometerComponent is not started");
        Counter errorCounter = Objects.requireNonNull(micrometer.getErrorCounter(),
                "MicrometerComponent is not started");
        AtomicInteger activeRequests = micrometer.getActiveRequests();

        Timer.Sample sample = Timer.start(micrometer.getRegistry());
        activeRequests.incrementAndGet();
        try {
            return chain.next(req);
        } catch (Exception ex) {
            errorCounter.increment();
            throw ex;
        } finally {
            sample.stop(requestTimer);
            activeRequests.decrementAndGet();
        }
    }
}
