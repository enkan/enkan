package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static enkan.web.util.HttpResponseUtils.getHeader;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class DefaultCharsetMiddlewareTest {
    private DefaultCharsetMiddleware middleware;

    @BeforeEach
    void setUp() {
        middleware = new DefaultCharsetMiddleware();
    }

    private HttpResponse handleWith(HttpResponse downstreamResponse) {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> downstreamResponse);
        return middleware.handle(new DefaultHttpRequest(), chain);
    }

    @Test
    void addsCharsetToTextHtml() {
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).contains("charset=UTF-8");
    }

    @Test
    void addsCharsetToApplicationXml() {
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "application/xml"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).contains("charset=UTF-8");
    }

    @Test
    void doesNotOverwriteExistingCharset() {
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html; charset=ISO-8859-1"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType)
                .contains("charset=ISO-8859-1")
                .doesNotContain("charset=UTF-8");
    }

    @Test
    void addsCharsetToApplicationJson() {
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "application/json"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).contains("charset=UTF-8");
    }

    @Test
    void addsCharsetToApplicationJsonWithExistingCharsetUnchanged() {
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "application/json; charset=UTF-8"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).containsOnlyOnce("charset=UTF-8");
    }

    @Test
    void addsCharsetToApplicationLdJson() {
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "application/ld+json"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).contains("charset=UTF-8");
    }

    @Test
    void addsCharsetToApplicationXhtmlXml() {
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "application/xhtml+xml"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).contains("charset=UTF-8");
    }

    @Test
    void doesNotAddCharsetToApplicationOctetStream() {
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "application/octet-stream"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).doesNotContain("charset");
    }

    @Test
    void addsCharsetPreservingExistingNonCharsetParameters() {
        // application/json; profile=... should keep profile param after charset is added.
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "application/json; profile=\"http://example.com/schema\""))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType)
                .contains("charset=UTF-8")
                .contains("profile=");
    }

    @Test
    void addsCharsetToCaseInsensitiveContentType() {
        // Media types are case-insensitive per RFC 7231 §3.1.1.1.
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "Application/JSON"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).contains("charset=UTF-8");
    }

    @Test
    void doesNothingWhenContentTypeAbsent() {
        HttpResponse response = HttpResponse.of("body");

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).isNull();
    }

    @Test
    void usesConfiguredDefaultCharset() {
        middleware.setDefaultCharset("Shift_JIS");
        HttpResponse response = builder(HttpResponse.of("body"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                .build();

        HttpResponse result = handleWith(response);

        String contentType = getHeader(result, "Content-Type");
        assertThat(contentType).contains("charset=Shift_JIS");
    }

    @Test
    void returnsNullWhenDownstreamReturnsNull() {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> null);

        HttpResponse result = middleware.handle(new DefaultHttpRequest(), chain);

        assertThat(result).isNull();
    }
}
