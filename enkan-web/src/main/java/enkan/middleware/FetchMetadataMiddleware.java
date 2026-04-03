package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Headers;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;

import java.util.Set;

import static enkan.util.BeanBuilder.builder;

/**
 * Implements a <a href="https://web.dev/articles/fetch-metadata">Resource Isolation Policy</a>
 * using W3C Fetch Metadata request headers ({@code Sec-Fetch-Site}, {@code Sec-Fetch-Mode}).
 *
 * <p>Modern browsers attach these headers to every request, and JavaScript cannot forge or
 * suppress them (they are Forbidden Headers per the Fetch Standard). This middleware uses
 * them to reject cross-origin, non-navigational requests at the framework level — blocking
 * CSRF, XSSI, and cross-origin information leaks without requiring per-form tokens.
 *
 * <h2>Default policy (Resource Isolation Policy)</h2>
 * <ol>
 *   <li>No {@code Sec-Fetch-Site} header → <b>allow</b> (non-browser or pre-Fetch-Metadata browser)</li>
 *   <li>{@code Sec-Fetch-Site: same-origin}, {@code same-site}, or {@code none} → <b>allow</b></li>
 *   <li>{@code Sec-Fetch-Mode: navigate} or {@code nested-navigate} on a {@code GET} request → <b>allow</b>
 *       (top-level navigation and cross-origin iframe navigation)</li>
 *   <li>URI matches the configured {@link #setAllowedPaths allow-list} → <b>allow</b> (public APIs)</li>
 *   <li>Everything else → <b>403 Forbidden</b></li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Default — blocks all cross-site non-navigational requests
 * app.use(new FetchMetadataMiddleware());
 *
 * // With a public API path that must accept cross-origin fetch
 * FetchMetadataMiddleware fm = new FetchMetadataMiddleware();
 * fm.setAllowedPaths(Set.of("/api/public/feed"));
 * app.use(fm);
 * }</pre>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Only modern browsers (Chrome 80+, Firefox 90+, Edge 86+, Safari 16.4+) send these
 *       headers. Requests from older browsers and non-browser clients pass through unchecked
 *       — combine with CSRF tokens for defence against those clients.</li>
 *   <li>{@link #setAllowedPaths} performs exact URI matching only. For pattern-based
 *       exclusions, subclass this middleware and override {@link #isAllowed}.</li>
 *   <li>When used together with {@code CorsMiddleware}, CORS-enabled paths must also be
 *       added to {@link #setAllowedPaths allowedPaths}; otherwise cross-origin preflight
 *       requests ({@code OPTIONS}, {@code Sec-Fetch-Mode: cors}) will be rejected with
 *       403 before {@code CorsMiddleware} can respond to them.</li>
 * </ul>
 *
 * @author kawasima
 */
@Middleware(name = "fetchMetadata")
public class FetchMetadataMiddleware implements WebMiddleware {

    private Set<String> allowedPaths = Set.of();

    /**
     * Applies the Resource Isolation Policy to the incoming request.
     * Allowed requests are forwarded to the next middleware; rejected requests
     * receive a {@code 403 Forbidden} response immediately.
     *
     * @param request the incoming HTTP request
     * @param chain   the remaining middleware chain
     * @return the HTTP response
     */
    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        if (!isAllowed(request)) {
            return builder(HttpResponse.of("Forbidden"))
                    .set(HttpResponse::setStatus, 403)
                    .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                    .build();
        }
        return castToHttpResponse(chain.next(request));
    }

    /**
     * Returns {@code true} if the request should be forwarded to the application.
     *
     * <p>Subclasses may override this method to implement custom policies while
     * reusing the default checks as a fallback.
     *
     * @param request the incoming HTTP request
     * @return {@code true} to allow; {@code false} to reject with 403
     */
    protected boolean isAllowed(HttpRequest request) {
        // 1. Allow if Sec-Fetch-Site is absent: treat as an unknown client (non-browser, legacy
        //    browser, or server-to-server) and allow by policy for compatibility.
        //    Note: non-browser clients *can* set arbitrary headers. The security guarantee is
        //    that browser JavaScript cannot set or modify Sec-Fetch-* headers — they are
        //    Forbidden Headers injected only by the browser's network layer.
        Headers headers = request.getHeaders();
        if (headers == null) return true;
        String fetchSite = headers.get("sec-fetch-site");
        if (fetchSite == null) return true;

        // 2. Allow same-origin and same-site requests — the request originates from the
        //    same site, so it is not a cross-origin attack.
        //    "none" means the request was triggered by direct user action (e.g. typed URL,
        //    bookmark), which is always safe.
        if ("same-origin".equals(fetchSite) || "same-site".equals(fetchSite)
                || "none".equals(fetchSite)) return true;

        // 3. Allow top-level GET navigation (user clicked a cross-origin link) and cross-origin
        //    iframe navigation (nested-navigate). POST navigations are intentionally excluded
        //    because cross-origin form submissions can be used for CSRF.
        String fetchMode = headers.get("sec-fetch-mode");
        if (("navigate".equals(fetchMode) || "nested-navigate".equals(fetchMode))
                && "GET".equals(request.getRequestMethod())) return true;

        // 4. Allow paths that are explicitly opted in to cross-origin access
        //    (e.g. public REST API endpoints).
        String uri = request.getUri();
        if (uri != null && allowedPaths.contains(uri)) return true;

        // 5. Reject all other cross-site requests (cors, no-cors, websocket, etc.)
        return false;
    }

    /**
     * Sets the exact URI paths that are permitted to receive cross-origin requests.
     * Use this for public API endpoints that must be reachable from other origins.
     *
     * <p>Only exact URI matches are considered (query strings are not included in
     * {@code HttpRequest#getUri()}).
     *
     * @param allowedPaths set of exact URIs, e.g. {@code Set.of("/api/public/feed")}
     */
    public void setAllowedPaths(Set<String> allowedPaths) {
        this.allowedPaths = Set.copyOf(allowedPaths);
    }
}
