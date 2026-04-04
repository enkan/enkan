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

import java.io.ByteArrayInputStream;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionalMiddlewareTest {
    private ConditionalMiddleware middleware;

    @BeforeEach
    void setup() {
        middleware = new ConditionalMiddleware();
    }

    private HttpResponse handle(HttpRequest request, HttpResponse downstream) {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> downstream);
        return middleware.handle(request, chain);
    }

    private HttpRequest get(String... headers) {
        Headers h = Headers.empty();
        for (int i = 0; i < headers.length; i += 2) {
            h.put(headers[i], headers[i + 1]);
        }
        return builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, h)
                .set(HttpRequest::setRequestMethod, "GET")
                .build();
    }

    private HttpResponse ok(String body, String... headers) {
        HttpResponse resp = HttpResponse.of(body);
        for (int i = 0; i < headers.length; i += 2) {
            resp.getHeaders().put(headers[i], headers[i + 1]);
        }
        return resp;
    }

    // --- ETag auto-generation ---

    @Test
    void autoGeneratesWeakETagForStringBody() {
        HttpResponse resp = handle(get(), ok("hello"));
        assertThat(resp.getHeaders().get("ETag")).isNotNull().startsWith("W/\"");
    }

    @Test
    void noETagForInputStreamBody() {
        HttpResponse downstream = HttpResponse.of(new ByteArrayInputStream(new byte[]{1}));
        HttpResponse resp = handle(get(), downstream);
        assertThat(resp.getHeaders().get("ETag")).isNull();
    }

    @Test
    void existingETagPreserved() {
        HttpResponse resp = handle(get(), ok("hello", "ETag", "\"custom\""));
        assertThat(resp.getHeaders().get("ETag")).isEqualTo("\"custom\"");
    }

    // --- If-None-Match ---

    @Test
    void ifNoneMatchHitReturns304() {
        HttpResponse downstream = ok("hello", "ETag", "\"abc\"");
        HttpResponse resp = handle(get("If-None-Match", "\"abc\""), downstream);
        assertThat(resp.getStatus()).isEqualTo(304);
        assertThat(resp.getBody()).isNull();
    }

    @Test
    void ifNoneMatchMissReturns200() {
        HttpResponse downstream = ok("hello", "ETag", "\"abc\"");
        HttpResponse resp = handle(get("If-None-Match", "\"xyz\""), downstream);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void ifNoneMatchWildcardReturns304() {
        HttpResponse downstream = ok("hello", "ETag", "\"abc\"");
        HttpResponse resp = handle(get("If-None-Match", "*"), downstream);
        assertThat(resp.getStatus()).isEqualTo(304);
    }

    @Test
    void ifNoneMatchOnPostReturns412() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("If-None-Match", "\"abc\""))
                .set(HttpRequest::setRequestMethod, "POST")
                .build();
        HttpResponse resp = handle(request, ok("hello", "ETag", "\"abc\""));
        assertThat(resp.getStatus()).isEqualTo(412);
    }

    // --- If-Modified-Since ---

    @Test
    void ifModifiedSinceNotModifiedReturns304() {
        HttpResponse resp = handle(
                get("If-Modified-Since", "Sun, 06 Nov 1994 08:49:37 GMT"),
                ok("hello", "Last-Modified", "Sat, 05 Nov 1994 08:49:37 GMT"));
        assertThat(resp.getStatus()).isEqualTo(304);
    }

    @Test
    void ifModifiedSinceModifiedReturns200() {
        HttpResponse resp = handle(
                get("If-Modified-Since", "Sat, 05 Nov 1994 08:49:37 GMT"),
                ok("hello", "Last-Modified", "Sun, 06 Nov 1994 08:49:37 GMT"));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void ifModifiedSinceIgnoredForPost() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("If-Modified-Since", "Sun, 06 Nov 2094 08:49:37 GMT"))
                .set(HttpRequest::setRequestMethod, "POST")
                .build();
        HttpResponse resp = handle(request, ok("hello", "Last-Modified", "Sat, 05 Nov 1994 08:49:37 GMT"));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void ifModifiedSinceIgnoredWhenIfNoneMatchPresent() {
        // If-None-Match miss → should NOT fall through to If-Modified-Since
        HttpResponse resp = handle(
                get("If-None-Match", "\"xyz\"",
                        "If-Modified-Since", "Sun, 06 Nov 2094 08:49:37 GMT"),
                ok("hello", "ETag", "\"abc\"",
                        "Last-Modified", "Sat, 05 Nov 1994 08:49:37 GMT"));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // --- If-Match ---

    @Test
    void ifMatchHitPassesThrough() {
        HttpResponse resp = handle(
                get("If-Match", "\"abc\""),
                ok("hello", "ETag", "\"abc\""));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void ifMatchMissReturns412() {
        HttpResponse resp = handle(
                get("If-Match", "\"abc\""),
                ok("hello", "ETag", "\"xyz\""));
        assertThat(resp.getStatus()).isEqualTo(412);
    }

    @Test
    void ifMatchRejectsWeakETag() {
        // Strong comparison: W/"abc" does not strongly match W/"abc"
        HttpResponse resp = handle(
                get("If-Match", "W/\"abc\""),
                ok("hello", "ETag", "W/\"abc\""));
        assertThat(resp.getStatus()).isEqualTo(412);
    }

    // --- If-Unmodified-Since ---

    @Test
    void ifUnmodifiedSinceModifiedReturns412() {
        HttpResponse resp = handle(
                get("If-Unmodified-Since", "Sat, 05 Nov 1994 08:49:37 GMT"),
                ok("hello", "Last-Modified", "Sun, 06 Nov 1994 08:49:37 GMT"));
        assertThat(resp.getStatus()).isEqualTo(412);
    }

    @Test
    void ifUnmodifiedSinceIgnoredWhenIfMatchPresent() {
        // If-Match hit → If-Unmodified-Since should not be evaluated
        HttpResponse resp = handle(
                get("If-Match", "\"abc\"",
                        "If-Unmodified-Since", "Sat, 01 Jan 1990 00:00:00 GMT"),
                ok("hello", "ETag", "\"abc\"",
                        "Last-Modified", "Sun, 06 Nov 1994 08:49:37 GMT"));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // --- Edge cases ---

    @Test
    void non2xxResponseNotEvaluated() {
        HttpResponse downstream = builder(HttpResponse.of("not found"))
                .set(HttpResponse::setStatus, 404)
                .build();
        HttpResponse resp = handle(get("If-None-Match", "*"), downstream);
        assertThat(resp.getStatus()).isEqualTo(404);
    }

    @Test
    void optionsMethodSkipsEvaluation() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("If-None-Match", "*"))
                .set(HttpRequest::setRequestMethod, "OPTIONS")
                .build();
        HttpResponse resp = handle(request, ok("hello", "ETag", "\"abc\""));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // --- 304 header requirements ---

    @Test
    void notModifiedPreservesRequiredHeaders() {
        HttpResponse downstream = ok("hello",
                "ETag", "\"abc\"",
                "Date", "Sun, 06 Nov 1994 08:49:37 GMT",
                "Vary", "Accept",
                "Cache-Control", "max-age=3600",
                "Expires", "Mon, 07 Nov 1994 08:49:37 GMT",
                "Content-Location", "/resource");
        HttpResponse resp = handle(get("If-None-Match", "\"abc\""), downstream);
        assertThat(resp.getStatus()).isEqualTo(304);
        assertThat(resp.getHeaders().get("ETag")).isEqualTo("\"abc\"");
        assertThat(resp.getHeaders().get("Date")).isNotNull();
        assertThat(resp.getHeaders().get("Vary")).isEqualTo("Accept");
        assertThat(resp.getHeaders().get("Cache-Control")).isEqualTo("max-age=3600");
        assertThat(resp.getHeaders().get("Expires")).isNotNull();
        assertThat(resp.getHeaders().get("Content-Location")).isEqualTo("/resource");
    }

    @Test
    void notModifiedStripsOtherHeaders() {
        HttpResponse downstream = ok("hello",
                "ETag", "\"abc\"",
                "Content-Type", "text/html",
                "Content-Length", "5");
        HttpResponse resp = handle(get("If-None-Match", "\"abc\""), downstream);
        assertThat(resp.getStatus()).isEqualTo(304);
        assertThat(resp.getHeaders().get("Content-Type")).isNull();
        assertThat(resp.getHeaders().get("Content-Length")).isNull();
    }

    @Test
    void contentEncodingAffectsAutoGeneratedETag() {
        HttpResponse plain = handle(get(), ok("hello"));
        HttpResponse gzipped = handle(get(), ok("hello", "Content-Encoding", "gzip"));
        assertThat(plain.getHeaders().get("ETag"))
                .isNotEqualTo(gzipped.getHeaders().get("ETag"));
    }

    // --- Additional boundary and edge case tests ---

    @Test
    void headMethodGets304ForIfNoneMatch() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("If-None-Match", "\"abc\""))
                .set(HttpRequest::setRequestMethod, "HEAD")
                .build();
        HttpResponse resp = handle(request, ok("hello", "ETag", "\"abc\""));
        assertThat(resp.getStatus()).isEqualTo(304);
    }

    @Test
    void ifModifiedSinceEqualTimestampReturns304() {
        // §13.1.3: "earlier or equal to" → condition false → 304
        String date = "Sun, 06 Nov 1994 08:49:37 GMT";
        HttpResponse resp = handle(
                get("If-Modified-Since", date),
                ok("hello", "Last-Modified", date));
        assertThat(resp.getStatus()).isEqualTo(304);
    }

    @Test
    void ifUnmodifiedSinceEqualTimestampPassesThrough() {
        // §13.1.4: "earlier than or equal to" → condition true → pass
        String date = "Sun, 06 Nov 1994 08:49:37 GMT";
        HttpResponse resp = handle(
                get("If-Unmodified-Since", date),
                ok("hello", "Last-Modified", date));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void ifMatchWildcardPassesThrough() {
        HttpResponse resp = handle(
                get("If-Match", "*"),
                ok("hello", "ETag", "\"abc\""));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void malformedIfModifiedSinceDateIsIgnored() {
        // §13.1.3: invalid date → ignore condition → normal response
        HttpResponse resp = handle(
                get("If-Modified-Since", "not-a-date"),
                ok("hello", "Last-Modified", "Sun, 06 Nov 1994 08:49:37 GMT"));
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void headMethodGets304ForIfModifiedSince() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of(
                        "If-Modified-Since", "Sun, 06 Nov 1994 08:49:37 GMT"))
                .set(HttpRequest::setRequestMethod, "HEAD")
                .build();
        HttpResponse resp = handle(request, ok("hello",
                "Last-Modified", "Sat, 05 Nov 1994 08:49:37 GMT"));
        assertThat(resp.getStatus()).isEqualTo(304);
    }
}
