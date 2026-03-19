package enkan.middleware.micrometer;

import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.component.micrometer.MicrometerComponent;
import io.micrometer.core.instrument.Timer;

import jakarta.inject.Inject;

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
        Timer.Sample sample = Timer.start(micrometer.getRegistry());
        micrometer.getActiveRequests().incrementAndGet();
        try {
            return chain.next(req);
        } catch (Exception ex) {
            micrometer.getErrorCounter().increment();
            throw ex;
        } finally {
            sample.stop(micrometer.getRequestTimer());
            micrometer.getActiveRequests().decrementAndGet();
        }
    }
}
