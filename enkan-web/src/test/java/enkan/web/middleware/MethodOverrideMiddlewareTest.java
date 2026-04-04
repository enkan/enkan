package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author kawasima
 */
class MethodOverrideMiddlewareTest {
    @Test
    void defaultIsParameter_method() {
        MethodOverrideMiddleware middleware = new MethodOverrideMiddleware();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setParams, Parameters.of("_method", "PUT"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ? ,?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    assertThat(req.getRequestMethod()).isEqualTo("PUT");
                    return builder(HttpResponse.of("hello")).build();
                });
        middleware.handle(request, chain);
    }

    @Test
    void overrideUsingByHeader() {
        MethodOverrideMiddleware middleware = builder(new MethodOverrideMiddleware())
                .set(MethodOverrideMiddleware::setGetterFunction, "X-Override-Method")
                .build();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setParams, Parameters.of("_method", "PUT"))
                .set(HttpRequest::setHeaders, Headers.of("X-Override-Method", "DELETE"))
                .build();
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    assertEquals("DELETE", req.getRequestMethod());
                    return builder(HttpResponse.of("hello")).build();
                });
        middleware.handle(request, chain);
    }
}
