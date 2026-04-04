package enkan.web.middleware;

import enkan.Endpoint;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static enkan.web.util.HttpResponseUtils.getHeader;
import static org.assertj.core.api.Assertions.assertThat;

class CspNonceMiddlewareTest {
    private HttpRequest request;
    private DefaultMiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> secChain;
    private SecurityHeadersMiddleware securityHeaders;

    @BeforeEach
    void setup() {
        request = new DefaultHttpRequest();

        Endpoint<HttpRequest, HttpResponse> endpoint =
                req -> builder(HttpResponse.of("hello")).build();
        DefaultMiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> endpointChain =
                new DefaultMiddlewareChain<>(new AnyPredicate<>(), null, endpoint);

        securityHeaders = new SecurityHeadersMiddleware();
        secChain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null, securityHeaders);
        secChain.setNext(endpointChain);
    }

    // --- Integration tests (CspNonce wrapping SecurityHeaders) ---

    @Test
    void nonceIsStoredInRequestExtension() {
        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        cspNonce.handle(request, secChain);

        String nonce = request.getExtension(CspNonceMiddleware.EXTENSION_KEY);
        assertThat(nonce).isNotNull().isNotBlank();
    }

    @Test
    void nonceIsGeneratedPerRequest() {
        CspNonceMiddleware cspNonce = new CspNonceMiddleware();

        cspNonce.handle(request, secChain);
        String nonce1 = request.getExtension(CspNonceMiddleware.EXTENSION_KEY);

        HttpRequest request2 = new DefaultHttpRequest();
        cspNonce.handle(request2, secChain);
        String nonce2 = request2.getExtension(CspNonceMiddleware.EXTENSION_KEY);

        assertThat(nonce1).isNotEqualTo(nonce2);
    }

    @Test
    void nonceIsBase64UrlWithoutPadding() {
        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        cspNonce.handle(request, secChain);

        String nonce = request.getExtension(CspNonceMiddleware.EXTENSION_KEY);
        // URL-safe Base64 must not contain standard Base64 chars '+', '/', or padding '='
        assertThat(nonce).doesNotContain("+", "/", "=");
    }

    @Test
    void cspHeaderContainsNonce() {
        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, secChain);

        assertThat((String) getHeader(response, "Content-Security-Policy"))
                .contains("'nonce-");
    }

    @Test
    void cspHeaderNonceMatchesRequestExtension() {
        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, secChain);

        String nonce = request.getExtension(CspNonceMiddleware.EXTENSION_KEY);
        assertThat((String) getHeader(response, "Content-Security-Policy"))
                .contains("'nonce-" + nonce + "'");
    }

    @Test
    void strictDynamicIsAddedWhenEnabled() {
        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        cspNonce.setStrictDynamic(true);
        HttpResponse response = cspNonce.handle(request, secChain);

        assertThat((String) getHeader(response, "Content-Security-Policy"))
                .contains("'strict-dynamic'");
    }

    @Test
    void strictDynamicIsAbsentByDefault() {
        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, secChain);

        assertThat((String) getHeader(response, "Content-Security-Policy"))
                .doesNotContain("'strict-dynamic'");
    }

    @Test
    void scriptSrcIsAddedWhenAbsent() {
        // Default CSP is "default-src 'self'" — no script-src
        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, secChain);

        assertThat((String) getHeader(response, "Content-Security-Policy"))
                .contains("script-src");
    }

    @Test
    void existingScriptSrcIsRewritten() {
        securityHeaders.setContentSecurityPolicy("script-src 'self'; default-src 'none'");

        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, secChain);

        String csp = getHeader(response, "Content-Security-Policy");
        assertThat(csp).contains("'nonce-");
        // script-src directive should contain the nonce
        String scriptSrcDirective = extractDirective(csp, "script-src");
        assertThat(scriptSrcDirective).contains("'nonce-");
    }

    @Test
    void strictDynamicNotDuplicated() {
        securityHeaders.setContentSecurityPolicy("script-src 'strict-dynamic'; default-src 'none'");

        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        cspNonce.setStrictDynamic(true);
        HttpResponse response = cspNonce.handle(request, secChain);

        String csp = getHeader(response, "Content-Security-Policy");
        long count = countOccurrences(csp, "'strict-dynamic'");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void nullCspIsNotRewritten() {
        securityHeaders.setContentSecurityPolicy(null);

        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, secChain);

        assertThat(response.getHeaders().containsKey("Content-Security-Policy")).isFalse();
    }

    @Test
    void nullResponseIsPassedThroughWithoutNpe() {
        // Endpoint that returns null (simulates a middleware that short-circuits with no response)
        Endpoint<HttpRequest, HttpResponse> nullEndpoint = req -> null;
        DefaultMiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> nullChain =
                new DefaultMiddlewareChain<>(new AnyPredicate<>(), null, nullEndpoint);

        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, nullChain);

        assertThat(response).isNull();
        // Nonce is still stored in the request extension even when response is null
        String nonce = request.getExtension(CspNonceMiddleware.EXTENSION_KEY);
        assertThat(nonce).isNotNull();
    }

    @Test
    void blankCspIsNotRewritten() {
        // A CSP set to whitespace-only should be treated as absent
        securityHeaders.setContentSecurityPolicy("   ");

        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, secChain);

        // Blank CSP was set by SecurityHeaders; CspNonce must not produce a nonce-only CSP
        String csp = getHeader(response, "Content-Security-Policy");
        // csp may be the whitespace string as-is (SecurityHeaders applied it);
        // CspNonceMiddleware must not have injected a nonce into it
        if (csp != null) {
            assertThat(csp).doesNotContain("'nonce-");
        }
    }

    @Test
    void onlyFirstScriptSrcDirectiveIsRewritten() {
        // Malformed CSP with two script-src directives — only the first must be rewritten
        securityHeaders.setContentSecurityPolicy("script-src 'self'; script-src 'unsafe-inline'");

        CspNonceMiddleware cspNonce = new CspNonceMiddleware();
        HttpResponse response = cspNonce.handle(request, secChain);

        String csp = getHeader(response, "Content-Security-Policy");
        // Nonce appears exactly once
        long count = countOccurrences(csp, "'nonce-");
        assertThat(count).isEqualTo(1);
        // The second script-src is left unchanged
        assertThat(csp).contains("script-src 'unsafe-inline'");
    }

    // --- Unit tests for injectNonce static method ---

    @Test
    void injectNonce_withExistingScriptSrc_appendsNonce() {
        String result = CspNonceMiddleware.injectNonce("script-src 'self'; default-src 'none'", "abc123", false);

        assertThat(result).contains("script-src 'self' 'nonce-abc123'");
        assertThat(result).contains("default-src 'none'");
    }

    @Test
    void injectNonce_withoutScriptSrc_prependsNewDirective() {
        String result = CspNonceMiddleware.injectNonce("default-src 'self'", "abc123", false);

        assertThat(result).startsWith("script-src 'nonce-abc123'");
        assertThat(result).contains("default-src 'self'");
    }

    @Test
    void injectNonce_mixedCaseScriptSrc_matchesCaseInsensitively() {
        String result = CspNonceMiddleware.injectNonce("Script-Src 'self'", "abc123", false);

        // The nonce must be appended to the existing directive (not prepended as a new one)
        assertThat(result).startsWith("Script-Src 'self' 'nonce-abc123'");
        // Only one script-src-like directive
        assertThat(countOccurrences(result.toLowerCase(java.util.Locale.ROOT), "script-src")).isEqualTo(1);
    }

    @Test
    void injectNonce_strictDynamicNotDuplicated_whenAlreadyPresent() {
        String result = CspNonceMiddleware.injectNonce("script-src 'strict-dynamic'", "abc123", true);

        long count = countOccurrences(result, "'strict-dynamic'");
        assertThat(count).isEqualTo(1);
        assertThat(result).contains("'nonce-abc123'");
    }

    @Test
    void injectNonce_scriptSrcWithNoSources_appendsNonce() {
        // Directive name only, no source list
        String result = CspNonceMiddleware.injectNonce("script-src; default-src 'none'", "abc123", false);

        assertThat(result).contains("script-src 'nonce-abc123'");
    }

    @Test
    void injectNonce_scriptSrcElemIsNotMatchedAsScriptSrc() {
        // script-src-elem must not be confused with script-src
        String result = CspNonceMiddleware.injectNonce("script-src-elem 'self'; default-src 'none'", "abc123", false);

        // A new script-src directive must be prepended — script-src-elem is left unchanged
        assertThat(result).startsWith("script-src 'nonce-abc123'");
        assertThat(result).contains("script-src-elem 'self'");
    }

    @Test
    void injectNonce_strictDynamicLikeTokenNotMatched() {
        // A hypothetical longer token sharing the 'strict-dynamic' prefix must not be counted
        // as already-present when checking for the real 'strict-dynamic' token
        String result = CspNonceMiddleware.injectNonce(
                "script-src 'strict-dynamic-extended'", "abc123", true);

        // 'strict-dynamic' was not present as a proper token, so it must be added
        assertThat(result).contains("'strict-dynamic'");
        // The original longer token is preserved unchanged
        assertThat(result).contains("'strict-dynamic-extended'");
    }

    // --- helpers ---

    private String extractDirective(String csp, String directiveName) {
        for (String part : csp.split(";")) {
            String trimmed = part.strip();
            if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith(directiveName.toLowerCase(java.util.Locale.ROOT) + " ")
                    || trimmed.equalsIgnoreCase(directiveName)) {
                return trimmed;
            }
        }
        return "";
    }

    private long countOccurrences(String text, String token) {
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
