package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static enkan.util.HttpResponseUtils.getHeader;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersMiddlewareTest {
    private HttpRequest request;
    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain;

    @BeforeEach
    void setup() {
        request = new DefaultHttpRequest();
        chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> builder(HttpResponse.of("hello")).build());
    }

    @Test
    void defaultHeadersAreApplied() {
        SecurityHeadersMiddleware middleware = new SecurityHeadersMiddleware();
        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Content-Security-Policy")).isEqualTo("default-src 'self'");
        assertThat((String) getHeader(response, "X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat((String) getHeader(response, "X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat((String) getHeader(response, "X-XSS-Protection")).isEqualTo("0");
        assertThat((String) getHeader(response, "Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat((String) getHeader(response, "Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
        assertThat((String) getHeader(response, "Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
        assertThat((String) getHeader(response, "Strict-Transport-Security")).contains("max-age=");
        assertThat((String) getHeader(response, "Cross-Origin-Embedder-Policy")).isEqualTo("require-corp");
        assertThat(response.getHeaders().containsKey("Permissions-Policy")).isFalse();
    }

    @Test
    void disabledHeaderIsNotPresent() {
        SecurityHeadersMiddleware middleware = new SecurityHeadersMiddleware();
        middleware.setStrictTransportSecurity(null);
        middleware.setContentSecurityPolicy(null);

        HttpResponse response = middleware.handle(request, chain);

        assertThat(response.getHeaders().containsKey("Strict-Transport-Security")).isFalse();
        assertThat(response.getHeaders().containsKey("Content-Security-Policy")).isFalse();
        // other headers still present
        assertThat((String) getHeader(response, "X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void customCspIsApplied() {
        SecurityHeadersMiddleware middleware = new SecurityHeadersMiddleware();
        middleware.setContentSecurityPolicy("default-src 'self'; img-src *");

        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Content-Security-Policy"))
                .isEqualTo("default-src 'self'; img-src *");
    }

    @Test
    void coepCanBeDisabled() {
        SecurityHeadersMiddleware middleware = new SecurityHeadersMiddleware();
        middleware.setCrossOriginEmbedderPolicy(null);

        HttpResponse response = middleware.handle(request, chain);

        assertThat(response.getHeaders().containsKey("Cross-Origin-Embedder-Policy")).isFalse();
    }

    @Test
    void credentiallessCoepIsApplied() {
        SecurityHeadersMiddleware middleware = new SecurityHeadersMiddleware();
        middleware.setCrossOriginEmbedderPolicy("credentialless");

        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Cross-Origin-Embedder-Policy"))
                .isEqualTo("credentialless");
    }

    @Test
    void permissionsPolicyIsAppliedWhenSet() {
        SecurityHeadersMiddleware middleware = new SecurityHeadersMiddleware();
        middleware.setPermissionsPolicy("camera=(), geolocation=(self)");

        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Permissions-Policy"))
                .isEqualTo("camera=(), geolocation=(self)");
    }

    @Test
    void customFrameOptionsIsApplied() {
        SecurityHeadersMiddleware middleware = new SecurityHeadersMiddleware();
        middleware.setFrameOptions("DENY");

        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "X-Frame-Options")).isEqualTo("DENY");
    }
}
