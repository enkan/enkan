package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * {@link CorsMiddleware} Test.
 *
 * @author syobochim
 */
class CorsMiddlewareTest {

    @Test
    void testPreflightRequest() {
        // SetUp
        HttpRequest request = builder(new DefaultHttpRequest()).build();
        request.setRequestMethod("OPTIONS");
        request.setHeaders(Headers.of("Origin", "http://sample.com",
                "Access-Control-Request-Method", "POST"));

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("")).set(HttpResponse::setStatus, 404).build());

        // Exercise
        CorsMiddleware sut = new CorsMiddleware();
        HttpResponse result = sut.handle(request, chain);

        // Verify
        assertThat(result.getStatus()).isEqualTo(200);
        Headers headers = result.getHeaders();
        assertThat(headers)
                .contains(entry("Access-Control-Allow-Origin", "*"))
                .contains(entry("Access-Control-Allow-Credentials", "true"));
        assertThat(headers).extracting("Access-Control-Allow-Methods")
                .asString()
                .contains("OPTIONS");
        assertThat(headers).extracting("Access-Control-Allow-Headers")
                .asString()
                .contains("Content-Type");
    }

    @Test
    void testAddHeaders() {
        // SetUp
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Origin", "http://sample.com"))
                .set(HttpRequest::setRequestMethod, "POST")
                .build();

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("hello"))
                        .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html")).build());

        // Exercise
        CorsMiddleware sut = new CorsMiddleware();
        HttpResponse result = sut.handle(request, chain);

        // Verify
        Headers headers = result.getHeaders();
        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(headers)
                .contains(entry("Access-Control-Allow-Origin", "*"),
                        entry("Content-Type", "text/html"));
        assertThat(result.getBodyAsString()).isEqualTo("hello");
    }

    @Test
    void testNonCORSRequest() {
        // Setup
        HttpRequest request = builder(new DefaultHttpRequest()).build();
        request.setHeaders(Headers.of("User-Agent", "Chrome"));

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("hello"))
                        .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html")).build());

        // Exercise
        CorsMiddleware sut = new CorsMiddleware();
        HttpResponse result = sut.handle(request, chain);

        // Verify
        Headers headers = result.getHeaders();
        assertThat(headers)
                .contains(entry("Content-Type", "text/html"))
                .doesNotContainKeys("Access-Control-Allow-Origin",
                        "Access-Control-Allow-Methods",
                        "Access-Control-Allow-Headers",
                        "Access-Control-Allow-Credentials");
        assertThat(result.getBodyAsString())
                .isEqualTo("hello");
    }

    @Test
    void testPreflightRequestWithLowerCaseMethod() {
        // SetUp
        HttpRequest request = builder(new DefaultHttpRequest()).build();
        request.setRequestMethod("options");
        request.setHeaders(Headers.of("Origin", "http://sample.com",
                "Access-Control-Request-Method", "POST"));

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("")).set(HttpResponse::setStatus, 404).build());

        // Exercise
        CorsMiddleware sut = new CorsMiddleware();
        HttpResponse result = sut.handle(request, chain);

        // Verify
        assertThat(result.getStatus()).isEqualTo(200);
        Headers headers = result.getHeaders();

        assertThat(headers)
                .contains(entry("Access-Control-Allow-Origin", "*"))
                .contains(entry("Access-Control-Allow-Credentials", "true"));
        assertThat(headers).extracting("Access-Control-Allow-Methods")
                .asString()
                .contains("OPTIONS");
        assertThat(headers).extracting("Access-Control-Allow-Headers")
                .asString()
                .contains("Content-Type");
    }

    @Test
    void preflightWithMultipleOriginsEchoesMatchedOriginAndSetsVary() {
        CorsMiddleware sut = new CorsMiddleware();
        sut.setOrigins(Set.of("https://a.example", "https://b.example"));

        HttpRequest request = builder(new DefaultHttpRequest()).build();
        request.setRequestMethod("OPTIONS");
        request.setHeaders(Headers.of("Origin", "https://a.example",
                "Access-Control-Request-Method", "POST"));

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("")).set(HttpResponse::setStatus, 404).build());

        HttpResponse result = sut.handle(request, chain);

        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getHeaders().get("Access-Control-Allow-Origin"))
                .isEqualTo("https://a.example");
        assertThat(result.getHeaders().get("Vary")).isEqualTo("Origin");
    }

    @Test
    void multipleConfiguredOriginsEchoesMatchedOrigin() {
        // RFC 6454 §7.2: Access-Control-Allow-Origin must be a single origin, not a list.
        // When multiple origins are configured, echo back only the matched request origin.
        CorsMiddleware sut = new CorsMiddleware();
        sut.setOrigins(Set.of("https://a.example", "https://b.example"));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Origin", "https://a.example"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build();

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("ok"))
                        .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain")).build());

        HttpResponse result = sut.handle(request, chain);

        assertThat(result.getHeaders().get("Access-Control-Allow-Origin"))
                .isEqualTo("https://a.example")
                .doesNotContain(",");
        assertThat(result.getHeaders().get("Vary")).isEqualTo("Origin");
    }

    @Test
    void varyOriginIsAppendedNotOverwritten() {
        // Vary already set by the endpoint; Origin must be appended, not overwrite it.
        CorsMiddleware sut = new CorsMiddleware();
        sut.setOrigins(Set.of("https://a.example"));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Origin", "https://a.example"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build();

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("ok"))
                        .set(HttpResponse::setHeaders, Headers.of("Vary", "Accept-Encoding")).build());

        HttpResponse result = sut.handle(request, chain);

        String vary = result.getHeaders().get("Vary");
        assertThat(vary).contains("Accept-Encoding");
        assertThat(vary).contains("Origin");
    }

    @Test
    void multipleConfiguredOriginsRejectsUnknownOrigin() {
        CorsMiddleware sut = new CorsMiddleware();
        sut.setOrigins(Set.of("https://a.example", "https://b.example"));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Origin", "https://evil.example"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build();

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("ok")).build());

        HttpResponse result = sut.handle(request, chain);

        // Unknown origin should receive a 403
        assertThat(result.getStatus()).isEqualTo(403);
    }

    @Test
    void testCORSRequestWithLowerCaseMethod()  {
        // SetUp
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders, Headers.of("Origin", "http://sample.com"))
                .set(HttpRequest::setRequestMethod, "post")
                .build();

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("hello"))
                        .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html")).build());

        // Exercise
        CorsMiddleware sut = new CorsMiddleware();
        HttpResponse result = sut.handle(request, chain);

        // Verify
        Headers headers = result.getHeaders();
        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(headers)
                .contains(entry("Access-Control-Allow-Origin", "*"),
                        entry("Content-Type", "text/html"));
        assertThat(result.getBodyAsString()).isEqualTo("hello");
    }

    @Test
    void setMaxAgeNullIsAccepted() {
        CorsMiddleware sut = new CorsMiddleware();
        sut.setMaxAge(null);

        HttpRequest request = builder(new DefaultHttpRequest()).build();
        request.setRequestMethod("OPTIONS");
        request.setHeaders(Headers.of("Origin", "http://sample.com",
                "Access-Control-Request-Method", "POST"));

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("")).set(HttpResponse::setStatus, 404).build());

        HttpResponse result = sut.handle(request, chain);
        assertThat(result.getHeaders()).doesNotContainKey("Access-Control-Max-Age");
    }

    @Test
    void setMaxAgeNegativeThrowsMisconfigurationException() {
        CorsMiddleware sut = new CorsMiddleware();
        assertThatThrownBy(() -> sut.setMaxAge(-1L))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void setMaxAgeZeroIsAccepted() {
        CorsMiddleware sut = new CorsMiddleware();
        sut.setMaxAge(0L);

        HttpRequest request = builder(new DefaultHttpRequest()).build();
        request.setRequestMethod("OPTIONS");
        request.setHeaders(Headers.of("Origin", "http://sample.com",
                "Access-Control-Request-Method", "POST"));

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("")).set(HttpResponse::setStatus, 404).build());

        HttpResponse result = sut.handle(request, chain);
        assertThat(result.getHeaders()).doesNotContainKey("Access-Control-Max-Age");
    }

    @Test
    void setMaxAgePositiveEmitsHeader() {
        CorsMiddleware sut = new CorsMiddleware();
        sut.setMaxAge(3600L);

        HttpRequest request = builder(new DefaultHttpRequest()).build();
        request.setRequestMethod("OPTIONS");
        request.setHeaders(Headers.of("Origin", "http://sample.com",
                "Access-Control-Request-Method", "POST"));

        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null, (Endpoint<HttpRequest, HttpResponse>) req ->
                builder(HttpResponse.of("")).set(HttpResponse::setStatus, 404).build());

        HttpResponse result = sut.handle(request, chain);
        assertThat(result.getHeaders().get("Access-Control-Max-Age")).isEqualTo("3600");
    }
}
