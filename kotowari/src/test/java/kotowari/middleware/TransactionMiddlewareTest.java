package kotowari.middleware;

import enkan.Endpoint;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.data.Routable;
import enkan.exception.MisconfigurationException;
import enkan.util.MixinUtils;
import enkan.util.Predicates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static enkan.util.ReflectionUtils.tryReflection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TransactionMiddleware}.
 */
public class TransactionMiddlewareTest {

    private TransactionMiddleware<HttpRequest, HttpResponse> middleware;

    @BeforeEach
    void setUp() {
        middleware = new TransactionMiddleware<>();
    }

    private HttpRequest buildRequest(Method controllerMethod) {
        HttpRequest request = new DefaultHttpRequest();
        request.setHeaders(Headers.empty());
        request.setRequestMethod("GET");
        request = MixinUtils.mixin(request, Routable.class);
        ((Routable) request).setControllerMethod(controllerMethod);
        return request;
    }

    private Endpoint<HttpRequest, HttpResponse> echoEndpoint() {
        return r -> HttpResponse.of("ok");
    }

    // --- Test controllers ---

    public static class NoTransactionController {
        public HttpResponse index() {
            return HttpResponse.of("no-tx");
        }
    }

    public static class TransactionalController {
        @jakarta.transaction.Transactional
        public HttpResponse save() {
            return HttpResponse.of("tx-required");
        }
    }

    /**
     * When the request is not Routable, the middleware passes through
     * to the next chain.
     */
    @Test
    void passThroughWhenRequestIsNotRoutable() {
        HttpRequest request = new DefaultHttpRequest();
        request.setHeaders(Headers.empty());
        request.setRequestMethod("GET");

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "test", echoEndpoint()));

        assertThat(response.getBodyAsString()).isEqualTo("ok");
    }

    /**
     * When the controller method has no @Transactional annotation,
     * the middleware passes through without starting a transaction.
     */
    @Test
    void passThroughWhenMethodHasNoTransactionalAnnotation() {
        Method method = tryReflection(() -> NoTransactionController.class.getMethod("index"));
        HttpRequest request = buildRequest(method);

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "test", echoEndpoint()));

        assertThat(response.getBodyAsString()).isEqualTo("ok");
    }

    /**
     * When the controller method is @Transactional but no TransactionComponent
     * is injected, the middleware should throw a MisconfigurationException.
     */
    @Test
    void throwsMisconfigurationWhenTransactionComponentIsMissing() {
        Method method = tryReflection(() -> TransactionalController.class.getMethod("save"));
        HttpRequest request = buildRequest(method);

        assertThatThrownBy(() -> middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "test", echoEndpoint())))
                .isInstanceOf(MisconfigurationException.class);
    }
}
