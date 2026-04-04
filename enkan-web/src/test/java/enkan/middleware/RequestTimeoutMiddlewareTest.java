package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestTimeoutMiddlewareTest {

    private RequestTimeoutMiddleware middleware;

    @BeforeEach
    void setup() {
        middleware = new RequestTimeoutMiddleware();
    }

    private HttpRequest getRequest() {
        return new DefaultHttpRequest();
    }

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chainReturning(String body, int status) {
        return new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of(body))
                                .set(HttpResponse::setStatus, status)
                                .build());
    }

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chainSleeping(long sleepMs) {
        return new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return HttpResponse.of("late");
                });
    }

    @Test
    void normalRequestCompletesBeforeTimeout() {
        middleware.setTimeoutMillis(5_000);
        HttpResponse response = middleware.handle(getRequest(), chainReturning("{}", 200));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{}");
    }

    @Test
    void slowHandlerReturns504() {
        middleware.setTimeoutMillis(100);
        HttpResponse response = middleware.handle(getRequest(), chainSleeping(500));

        assertThat(response.getStatus()).isEqualTo(504);
        assertThat(response.getBody()).isEqualTo("Gateway Timeout");
    }

    @Test
    void customTimeoutStatusIsReturned() {
        middleware.setTimeoutMillis(100);
        middleware.setTimeoutStatus(503);
        HttpResponse response = middleware.handle(getRequest(), chainSleeping(500));

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void handlerExceptionPropagatesIndependentlyOfTimeout() {
        middleware.setTimeoutMillis(5_000);
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    throw new RuntimeException("handler error");
                });

        assertThatThrownBy(() -> middleware.handle(getRequest(), chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("handler error");
    }

    @Test
    void zeroTimeoutMillisThrowsMisconfigurationException() {
        assertThatThrownBy(() -> middleware.setTimeoutMillis(0))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void scopedValueIsInheritedBySubtask() throws Exception {
        var user = ScopedValue.<String>newInstance();
        middleware.setTimeoutMillis(5_000);

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        HttpResponse.of(user.isBound() ? user.get() : "<not bound>"));

        HttpResponse response = ScopedValue.where(user, "duke").call(
                () -> middleware.handle(getRequest(), chain));

        assertThat(response.getBody()).isEqualTo("duke");
    }
}
