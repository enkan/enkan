package kotowari.middleware;

import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.data.Routable;
import enkan.system.inject.ComponentInjector;
import enkan.util.MixinUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static enkan.util.ReflectionUtils.tryReflection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ControllerInvokerMiddlewareTest {

    private ControllerInvokerMiddleware<HttpResponse> middleware;

    // Test controllers — public so LambdaMetafactory/MethodHandle can access them
    public static class ZeroArgController {
        public HttpResponse index() {
            return HttpResponse.of("zero");
        }
    }

    public static class OneArgController {
        public HttpResponse show(Parameters params) {
            return HttpResponse.of("one:" + params.get("id"));
        }
    }

    public static class TwoArgController {
        public HttpResponse update(Parameters params, HttpRequest request) {
            return HttpResponse.of("two:" + params.get("id") + ":" + request.getRequestMethod());
        }
    }

    public static class ThreeArgController {
        public String multi(Parameters params, HttpRequest request, HttpRequest request2) {
            return "three";
        }
    }

    public static class ThrowingController {
        public HttpResponse fail() {
            throw new IllegalStateException("controller error");
        }

        public HttpResponse failChecked() throws Exception {
            throw new Exception("checked error");
        }
    }

    @BeforeEach
    void setUp() {
        middleware = new ControllerInvokerMiddleware<>(new ComponentInjector(null));
        middleware.setupParameterInjectors();
    }

    private HttpRequest buildRequest(Method controllerMethod) {
        HttpRequest request = new DefaultHttpRequest();
        request.setHeaders(Headers.empty());
        request.setRequestMethod("GET");
        request.setQueryString("");
        request.setParams(Parameters.of("id", "42"));
        request = MixinUtils.mixin(request, Routable.class);
        ((Routable) request).setControllerMethod(controllerMethod);
        return request;
    }

    @Test
    void invokeZeroArgController() {
        Method method = tryReflection(() -> ZeroArgController.class.getMethod("index"));
        HttpRequest request = buildRequest(method);

        HttpResponse response = middleware.handle(request, null);

        assertThat(response.getBodyAsString()).isEqualTo("zero");
    }

    @Test
    void invokeOneArgController() {
        Method method = tryReflection(() -> OneArgController.class.getMethod("show", Parameters.class));
        HttpRequest request = buildRequest(method);

        HttpResponse response = middleware.handle(request, null);

        assertThat(response.getBodyAsString()).isEqualTo("one:42");
    }

    @Test
    void invokeTwoArgController() {
        Method method = tryReflection(() -> TwoArgController.class.getMethod("update", Parameters.class, HttpRequest.class));
        HttpRequest request = buildRequest(method);

        HttpResponse response = middleware.handle(request, null);

        assertThat(response.getBodyAsString()).isEqualTo("two:42:GET");
    }

    @Test
    void invokeThreeArgController() {
        Method method = tryReflection(() -> ThreeArgController.class.getMethod("multi", Parameters.class, HttpRequest.class, HttpRequest.class));
        HttpRequest request = buildRequest(method);

        // String return type — should still work
        Object response = middleware.handle(request, null);

        assertThat(response).isEqualTo("three");
    }

    @Test
    void runtimeExceptionPropagatesDirectly() {
        Method method = tryReflection(() -> ThrowingController.class.getMethod("fail"));
        HttpRequest request = buildRequest(method);

        assertThatThrownBy(() -> middleware.handle(request, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("controller error");
    }

    @Test
    void checkedExceptionWrappedInRuntimeException() {
        Method method = tryReflection(() -> ThrowingController.class.getMethod("failChecked"));
        HttpRequest request = buildRequest(method);

        assertThatThrownBy(() -> middleware.handle(request, null))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(Exception.class)
                .hasRootCauseMessage("checked error");
    }

    @Test
    void cachedInvokerReturnsSameResultOnSecondCall() {
        Method method = tryReflection(() -> ZeroArgController.class.getMethod("index"));
        HttpRequest request1 = buildRequest(method);
        HttpRequest request2 = buildRequest(method);

        HttpResponse response1 = middleware.handle(request1, null);
        HttpResponse response2 = middleware.handle(request2, null);

        assertThat(response1.getBodyAsString()).isEqualTo("zero");
        assertThat(response2.getBodyAsString()).isEqualTo("zero");
    }
}
