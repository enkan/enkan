package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static enkan.web.util.HttpResponseUtils.getHeader;
import static enkan.web.util.HttpResponseUtils.header;

/**
 * Generates a cryptographically random per-request CSP nonce and injects it into the
 * {@code Content-Security-Policy} response header set by {@link SecurityHeadersMiddleware}.
 *
 * <p>This middleware must be placed <em>before</em> {@link SecurityHeadersMiddleware} in the
 * chain (i.e., as an outer wrapper) so that it can rewrite the CSP header after
 * {@code SecurityHeadersMiddleware} has added it to the response.
 *
 * <p>The generated nonce is stored in the request extension under the key
 * {@link #EXTENSION_KEY} and is accessible from controllers and templates:
 *
 * <pre>{@code
 * String nonce = request.getExtension(CspNonceMiddleware.EXTENSION_KEY);
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Basic — nonce injected into script-src
 * app.use(new CspNonceMiddleware());
 * app.use(new SecurityHeadersMiddleware());
 *
 * // With strict-dynamic
 * CspNonceMiddleware nonce = new CspNonceMiddleware();
 * nonce.setStrictDynamic(true);
 * app.use(nonce);
 * app.use(new SecurityHeadersMiddleware());
 * }</pre>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Only the {@code script-src} directive is rewritten. The finer-grained
 *       {@code script-src-elem} and {@code script-src-attr} directives (CSP Level 3) are
 *       not modified. If your policy uses those directives instead of {@code script-src},
 *       you must inject the nonce manually.</li>
 *   <li>If {@link SecurityHeadersMiddleware} has {@code Content-Security-Policy} disabled
 *       ({@code setContentSecurityPolicy(null)}), no CSP header will be present in the
 *       response. The nonce is still stored in the request extension, but it will not be
 *       reflected in any response header.</li>
 * </ul>
 *
 * @author kawasima
 */
@Middleware(name = "cspNonce")
public class CspNonceMiddleware implements WebMiddleware {

    /** Request extension key under which the nonce value is stored. */
    public static final String EXTENSION_KEY = "cspNonce";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder NONCE_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int NONCE_BYTES = 16; // 128 bits — CSP Level 3 minimum
    private static final int SCRIPT_SRC_LENGTH = "script-src".length();

    private boolean strictDynamic = false;

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        String nonce = generateNonce();
        request.setExtension(EXTENSION_KEY, nonce);

        HttpResponse response = castToHttpResponse(chain.next(request));
        if (response == null) return null;

        rewriteCspHeader(response, nonce);
        return response;
    }

    private String generateNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        // Use URL-safe Base64 without padding per W3C CSP Level 3 recommendation
        return NONCE_ENCODER.encodeToString(bytes);
    }

    private void rewriteCspHeader(HttpResponse response, String nonce) {
        String existing = getHeader(response, "Content-Security-Policy");
        if (existing == null || existing.isBlank()) return;
        // Remove the existing header before writing the rewritten value, because
        // Parameters.put() accumulates multiple values into a list rather than replacing.
        response.getHeaders().remove("Content-Security-Policy");
        header(response, "Content-Security-Policy", injectNonce(existing, nonce, strictDynamic));
    }

    /**
     * Injects a CSP nonce token into the {@code script-src} directive of a CSP string.
     *
     * <p>If no {@code script-src} directive is present, one is prepended. If
     * {@code strictDynamic} is {@code true}, {@code 'strict-dynamic'} is added to
     * {@code script-src} unless it is already present.
     *
     * <p>Directive names are matched case-insensitively per the CSP specification.
     * Only {@code script-src} is targeted; {@code script-src-elem} and
     * {@code script-src-attr} are left unchanged.
     *
     * @param csp           the original CSP directive string
     * @param nonce         the base64url-encoded nonce value
     * @param strictDynamic whether to add {@code 'strict-dynamic'} to {@code script-src}
     * @return the rewritten CSP string
     */
    static String injectNonce(String csp, String nonce, boolean strictDynamic) {
        List<String> directives = splitDirectives(csp);

        String nonceToken = "'nonce-" + nonce + "'";
        boolean found = false;

        for (int i = 0; i < directives.size(); i++) {
            String d = directives.get(i);
            // Find the end of the directive name (first whitespace, or end of string)
            int nameEnd = d.length();
            for (int j = 0; j < d.length(); j++) {
                if (d.charAt(j) == ' ' || d.charAt(j) == '\t') {
                    nameEnd = j;
                    break;
                }
            }
            // Match exactly "script-src" (case-insensitive, per CSP spec).
            // nameEnd must equal SCRIPT_SRC_LENGTH to rule out longer names like
            // "script-src-elem". regionMatches returns false when len exceeds other.length(),
            // so the explicit length guard here also serves as documentation.
            if (nameEnd == SCRIPT_SRC_LENGTH
                    && d.regionMatches(true, 0, "script-src", 0, SCRIPT_SRC_LENGTH)) {
                d = d + " " + nonceToken;
                if (strictDynamic && !containsToken(d, "'strict-dynamic'")) {
                    d = d + " 'strict-dynamic'";
                }
                directives.set(i, d);
                found = true;
                break;
            }
        }

        if (!found) {
            String newDirective = "script-src " + nonceToken;
            if (strictDynamic) {
                newDirective += " 'strict-dynamic'";
            }
            directives.add(0, newDirective);
        }

        return String.join("; ", directives);
    }

    /**
     * Splits a CSP string into a mutable list of trimmed, non-blank directives.
     * Uses an {@code indexOf} loop to avoid regex allocation.
     */
    private static List<String> splitDirectives(String csp) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int len = csp.length();
        while (start < len) {
            int semi = csp.indexOf(';', start);
            String segment = semi < 0
                    ? csp.substring(start).strip()
                    : csp.substring(start, semi).strip();
            if (!segment.isEmpty()) {
                result.add(segment);
            }
            if (semi < 0) break;
            start = semi + 1;
        }
        return result;
    }

    /**
     * Returns {@code true} if {@code directive} contains {@code token} as a
     * whitespace-delimited, case-insensitive token match.
     *
     * <p>A match is only counted if the token is surrounded by whitespace or string
     * boundaries, preventing false positives from longer tokens that share a prefix
     * (e.g. a hypothetical {@code 'strict-dynamic-extended'} must not match
     * {@code 'strict-dynamic'}).
     */
    private static boolean containsToken(String directive, String token) {
        int idx = 0;
        int dLen = directive.length();
        int tLen = token.length();
        while (idx + tLen <= dLen) {
            if (directive.regionMatches(true, idx, token, 0, tLen)) {
                boolean beforeOk = idx == 0 || isAsciiWhitespace(directive.charAt(idx - 1));
                boolean afterOk = idx + tLen == dLen || isAsciiWhitespace(directive.charAt(idx + tLen));
                if (beforeOk && afterOk) return true;
            }
            idx++;
        }
        return false;
    }

    private static boolean isAsciiWhitespace(char c) {
        return c == ' ' || c == '\t';
    }

    /**
     * Sets whether {@code 'strict-dynamic'} is added to the {@code script-src} directive.
     *
     * @param strictDynamic {@code true} to enable {@code 'strict-dynamic'}
     */
    public void setStrictDynamic(boolean strictDynamic) {
        this.strictDynamic = strictDynamic;
    }
}
