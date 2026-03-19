package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class ForwardedSchemeMiddlewareTest {
    private ForwardedSchemeMiddleware middleware;
    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain;

    @BeforeEach
    void setup() {
        middleware = new ForwardedSchemeMiddleware();
        chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("ok")).build());
    }

    @Test
    void setsSchemeToHttpsFromForwardedProtoHeader() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("X-Forwarded-Proto", "https"))
                .set(HttpRequest::setScheme, "http")
                .build();

        middleware.handle(request, chain);
        assertThat(request.getScheme()).isEqualTo("https");
    }

    @Test
    void setsSchemeToHttpFromForwardedProtoHeader() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("X-Forwarded-Proto", "http"))
                .set(HttpRequest::setScheme, "https")
                .build();

        middleware.handle(request, chain);
        assertThat(request.getScheme()).isEqualTo("http");
    }

    @Test
    void keepsOriginalSchemeWhenNoForwardedProtoHeader() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.empty())
                .set(HttpRequest::setScheme, "http")
                .build();

        middleware.handle(request, chain);
        assertThat(request.getScheme()).isEqualTo("http");
    }

    @Test
    void headerNameIsCaseInsensitive() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("x-forwarded-proto", "https"))
                .set(HttpRequest::setScheme, "http")
                .build();

        middleware.handle(request, chain);
        assertThat(request.getScheme()).isEqualTo("https");
    }
}
