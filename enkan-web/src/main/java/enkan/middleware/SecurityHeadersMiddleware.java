package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;

import java.time.Duration;

import static enkan.util.HttpResponseUtils.header;

/**
 * Applies a suite of security-related HTTP response headers in a single middleware,
 * similar to the <a href="https://helmetjs.github.io/">Helmet.js</a> library for Express.
 *
 * <p>All headers are enabled by default with safe values. Individual headers can be
 * disabled by setting their option to {@code null}, or customized via the provided
 * setter methods.
 *
 * <h2>Default headers</h2>
 * <ul>
 *   <li>{@code Content-Security-Policy: default-src 'self'}</li>
 *   <li>{@code Strict-Transport-Security: max-age=15552000; includeSubDomains}</li>
 *   <li>{@code X-Content-Type-Options: nosniff}</li>
 *   <li>{@code X-Frame-Options: SAMEORIGIN}</li>
 *   <li>{@code X-XSS-Protection: 0} (disabled — browsers rely on CSP instead)</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}</li>
 *   <li>{@code Cross-Origin-Opener-Policy: same-origin}</li>
 *   <li>{@code Cross-Origin-Resource-Policy: same-origin}</li>
 *   <li>{@code Cross-Origin-Embedder-Policy: require-corp}</li>
 *   <li>{@code Permissions-Policy} — disabled by default (no safe universal default exists)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // All defaults
 * app.use(new SecurityHeadersMiddleware());
 *
 * // Custom CSP, disable HSTS (e.g. during local development)
 * SecurityHeadersMiddleware sec = new SecurityHeadersMiddleware();
 * sec.setContentSecurityPolicy("default-src 'self'; img-src *");
 * sec.setStrictTransportSecurity(null);  // disable
 * app.use(sec);
 * }</pre>
 *
 * @author kawasima
 */
@Middleware(name = "securityHeaders")
public class SecurityHeadersMiddleware implements WebMiddleware {
    /**
     * Default HSTS max-age: 180 days in seconds.
     */
    private static final long DEFAULT_HSTS_MAX_AGE = Duration.ofDays(180).toSeconds();

    // --- header values (null = disabled) ---

    private String contentSecurityPolicy = "default-src 'self'";
    private String strictTransportSecurity = "max-age=" + DEFAULT_HSTS_MAX_AGE + "; includeSubDomains";
    private String contentTypeOptions = "nosniff";
    private String frameOptions = "SAMEORIGIN";
    /** Set to "0" to disable the legacy XSS auditor (modern browsers use CSP instead). */
    private String xssProtection = "0";
    private String referrerPolicy = "strict-origin-when-cross-origin";
    private String crossOriginOpenerPolicy = "same-origin";
    private String crossOriginResourcePolicy = "same-origin";
    private String crossOriginEmbedderPolicy = "require-corp";
    /** Disabled by default — no safe universal default exists. */
    private String permissionsPolicy;

    /**
     * Passes the request through the chain and applies all enabled security
     * headers to the response.
     *
     * @param request the incoming HTTP request
     * @param chain   the remaining middleware chain
     * @return the HTTP response with security headers applied
     */
    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        HttpResponse response = castToHttpResponse(chain.next(request));
        if (response == null) return null;

        applyIfEnabled(response, "Content-Security-Policy", contentSecurityPolicy);
        applyIfEnabled(response, "Strict-Transport-Security", strictTransportSecurity);
        applyIfEnabled(response, "X-Content-Type-Options", contentTypeOptions);
        applyIfEnabled(response, "X-Frame-Options", frameOptions);
        applyIfEnabled(response, "X-XSS-Protection", xssProtection);
        applyIfEnabled(response, "Referrer-Policy", referrerPolicy);
        applyIfEnabled(response, "Cross-Origin-Opener-Policy", crossOriginOpenerPolicy);
        applyIfEnabled(response, "Cross-Origin-Resource-Policy", crossOriginResourcePolicy);
        applyIfEnabled(response, "Cross-Origin-Embedder-Policy", crossOriginEmbedderPolicy);
        applyIfEnabled(response, "Permissions-Policy", permissionsPolicy);

        return response;
    }

    private void applyIfEnabled(HttpResponse response, String name, String value) {
        if (value != null) {
            header(response, name, value);
        }
    }

    // --- setters (pass null to disable the header) ---

    /**
     * Sets the {@code Content-Security-Policy} header value.
     * Pass {@code null} to disable this header.
     *
     * @param contentSecurityPolicy CSP directive string, e.g. {@code "default-src 'self'"}
     */
    public void setContentSecurityPolicy(String contentSecurityPolicy) {
        this.contentSecurityPolicy = contentSecurityPolicy;
    }

    /**
     * Sets the {@code Strict-Transport-Security} header value.
     * Pass {@code null} to disable this header (e.g. during local development).
     *
     * @param strictTransportSecurity HSTS directive string, e.g. {@code "max-age=15552000; includeSubDomains"}
     */
    public void setStrictTransportSecurity(String strictTransportSecurity) {
        this.strictTransportSecurity = strictTransportSecurity;
    }

    /**
     * Sets the {@code X-Content-Type-Options} header value.
     * Pass {@code null} to disable.
     *
     * @param contentTypeOptions typically {@code "nosniff"}
     */
    public void setContentTypeOptions(String contentTypeOptions) {
        this.contentTypeOptions = contentTypeOptions;
    }

    /**
     * Sets the {@code X-Frame-Options} header value.
     * Valid values are {@code "DENY"} and {@code "SAMEORIGIN"}.
     * Pass {@code null} to disable.
     *
     * @param frameOptions frame options value
     */
    public void setFrameOptions(String frameOptions) {
        this.frameOptions = frameOptions;
    }

    /**
     * Sets the {@code X-XSS-Protection} header value.
     * The recommended modern value is {@code "0"} (disabled) because browsers rely on CSP.
     * Pass {@code null} to omit the header entirely.
     *
     * @param xssProtection header value
     */
    public void setXssProtection(String xssProtection) {
        this.xssProtection = xssProtection;
    }

    /**
     * Sets the {@code Referrer-Policy} header value.
     * Pass {@code null} to disable.
     *
     * @param referrerPolicy policy value, e.g. {@code "strict-origin-when-cross-origin"}
     */
    public void setReferrerPolicy(String referrerPolicy) {
        this.referrerPolicy = referrerPolicy;
    }

    /**
     * Sets the {@code Cross-Origin-Opener-Policy} header value.
     * Pass {@code null} to disable.
     *
     * @param crossOriginOpenerPolicy policy value, e.g. {@code "same-origin"}
     */
    public void setCrossOriginOpenerPolicy(String crossOriginOpenerPolicy) {
        this.crossOriginOpenerPolicy = crossOriginOpenerPolicy;
    }

    /**
     * Sets the {@code Cross-Origin-Resource-Policy} header value.
     * Pass {@code null} to disable.
     *
     * @param crossOriginResourcePolicy policy value, e.g. {@code "same-origin"}
     */
    public void setCrossOriginResourcePolicy(String crossOriginResourcePolicy) {
        this.crossOriginResourcePolicy = crossOriginResourcePolicy;
    }

    /**
     * Sets the {@code Cross-Origin-Embedder-Policy} header value.
     * Required (together with COOP) to enable {@code crossOriginIsolated} context
     * in browsers, which gates {@code SharedArrayBuffer} and high-resolution timers.
     * Pass {@code null} to disable.
     *
     * @param crossOriginEmbedderPolicy policy value, e.g. {@code "require-corp"} or {@code "credentialless"}
     */
    public void setCrossOriginEmbedderPolicy(String crossOriginEmbedderPolicy) {
        this.crossOriginEmbedderPolicy = crossOriginEmbedderPolicy;
    }

    /**
     * Sets the {@code Permissions-Policy} header value.
     * Controls which browser features (camera, geolocation, payment, etc.) the page
     * and its iframes are allowed to use.
     * Disabled by default ({@code null}) because no safe universal default exists.
     * Pass {@code null} to disable.
     *
     * @param permissionsPolicy policy value, e.g. {@code "camera=(), geolocation=(self)"}
     */
    public void setPermissionsPolicy(String permissionsPolicy) {
        this.permissionsPolicy = permissionsPolicy;
    }
}
