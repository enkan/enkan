package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.data.*;
import enkan.web.data.*;
import enkan.util.MixinUtils;
import enkan.util.Predicates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class FlashMiddlewareTest {
    private FlashMiddleware middleware;
    private HttpRequest request;

    @BeforeEach
    void setup() {
        Session session = new Session();
        session.put("_flash", new Flash<>("message"));
        middleware = new FlashMiddleware();
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setSession, session)
                .build();
    }

    @Test
    void getFlash_and_NoResponseFlash() {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(Predicates.any(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("hello"))
                                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html"))
                                .build());
        request = MixinUtils.mixin(request, FlashAvailable.class);
        HttpResponse response = middleware.handle(request, chain);
        assertThat(request.getFlash().value()).isEqualTo("message");
        assertThat(request.getSession())
                .isNotNull()
                .doesNotContainKeys("_flash");
        assertThat(response.getFlash()).isNull();
    }

    @Test
    void setFlash() {
        //noinspection unchecked
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(Predicates.any(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("hello"))
                                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html"))
                                .set(HttpResponse::setFlash, new Flash<>("new flash"))
                                .build());
        HttpResponse response = middleware.handle(request, chain);
        assertThat(request.getFlash().value()).isEqualTo("message");
        assertThat(response.getFlash().value()).isEqualTo("new flash");
    }

}