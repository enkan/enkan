package enkan.middleware.micrometer;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.component.LifecycleManager;
import enkan.component.micrometer.MicrometerComponent;
import enkan.util.Predicates;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicrometerMiddlewareTest {

    private MicrometerComponent micrometerComponent;
    private MicrometerMiddleware<Object, Object> middleware;

    @BeforeEach
    void setUp() throws Exception {
        micrometerComponent = new MicrometerComponent();
        LifecycleManager.start(micrometerComponent);

        middleware = new MicrometerMiddleware<>();
        Field f = MicrometerMiddleware.class.getDeclaredField("micrometer");
        f.setAccessible(true);
        f.set(middleware, micrometerComponent);
    }

    @AfterEach
    void tearDown() {
        LifecycleManager.stop(micrometerComponent);
    }

    private MiddlewareChain<Object, Object, Object, Object> chainOf(Function<Object, Object> fn) {
        Middleware<Object, Object, Object, Object> endpoint = new Middleware<>() {
            @Override
            public <NNREQ, NNRES> Object handle(Object req,
                    MiddlewareChain<Object, Object, NNREQ, NNRES> chain) {
                return fn.apply(req);
            }
        };
        DefaultMiddlewareChain<Object, Object, Object, Object> metricsChain =
                new DefaultMiddlewareChain<>(Predicates.any(), "micrometer", middleware);
        DefaultMiddlewareChain<Object, Object, Object, Object> endpointChain =
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint", endpoint);
        metricsChain.setNext(endpointChain);
        return metricsChain;
    }

    @Test
    void activeRequestsIsIncrementedThenDecremented() {
        AtomicInteger activeRequests = micrometerComponent.getActiveRequests();
        assertThat(activeRequests.get()).isZero();

        AtomicInteger inFlightSnapshot = new AtomicInteger();
        chainOf(req -> {
            inFlightSnapshot.set(activeRequests.get());
            return "res";
        }).next("req");

        assertThat(inFlightSnapshot.get()).isEqualTo(1);
        assertThat(activeRequests.get()).isZero();
    }

    @Test
    void requestTimerIsUpdatedOnSuccess() {
        Timer timer = micrometerComponent.getRequestTimer();
        assertThat(timer.count()).isZero();

        chainOf(req -> "res").next("req");

        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void errorCounterIsIncrementedOnException() {
        assertThat(micrometerComponent.getErrorCounter().count()).isZero();

        assertThatThrownBy(() ->
                chainOf(req -> { throw new RuntimeException("boom"); }).next("req")
        ).isInstanceOf(RuntimeException.class).hasMessage("boom");

        assertThat(micrometerComponent.getErrorCounter().count()).isEqualTo(1);
    }

    @Test
    void activeRequestsIsDecrementedOnException() {
        assertThatThrownBy(() ->
                chainOf(req -> { throw new RuntimeException("boom"); }).next("req")
        ).isInstanceOf(RuntimeException.class);

        assertThat(micrometerComponent.getActiveRequests().get()).isZero();
    }

    @Test
    void timerIsUpdatedOnException() {
        Timer timer = micrometerComponent.getRequestTimer();

        assertThatThrownBy(() ->
                chainOf(req -> { throw new RuntimeException("boom"); }).next("req")
        ).isInstanceOf(RuntimeException.class);

        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void errorsAreNotIncrementedOnSuccess() {
        chainOf(req -> "res").next("req");

        assertThat(micrometerComponent.getErrorCounter().count()).isZero();
    }
}
