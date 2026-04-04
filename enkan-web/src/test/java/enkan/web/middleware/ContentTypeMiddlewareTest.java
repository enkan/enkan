package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import enkan.web.util.HttpResponseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class ContentTypeMiddlewareTest {
    private ContentTypeMiddleware middleware;
    private HttpRequest request;

    @BeforeEach
    void setup() {
        middleware = new ContentTypeMiddleware();
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Host", "example.com"))
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setUri, "/prefix/")
                .set(HttpRequest::setQueryString, "a=b&c=d")
                .build();
    }

    @Test
    void testSetContentType() {
        MiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("hello"))
                                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html"))
                                .build());

        HttpResponse response = middleware.handle(request, chain);
        final Object header = HttpResponseUtils.getHeader(response, "content-type");
        assertThat(header)
                .asString()
                .isEqualTo("text/html");
    }

    @Test
    void defaultValueIsOctetStream() {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("hello"))
                                .build());

        HttpResponse response = middleware.handle(request, chain);
        final Object header = HttpResponseUtils.getHeader(response, "content-type");
        assertThat(header).isEqualTo("text/plain");
    }

    @Test
    void testRedirect() {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("hello"))
                                .set(HttpResponse::setStatus, 303)
                                .set(HttpResponse::setHeaders, Headers.of("Location", "foo/bar"))
                                .build());

        HttpResponse response = middleware.handle(request, chain);
        assertThat((String)HttpResponseUtils.getHeader(response, "content-type"))
                .as("This is a specification of ring content_type middleware")
                .isEqualTo("text/plain");
    }
}
