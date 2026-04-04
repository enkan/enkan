package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;

import java.time.Duration;
import java.util.regex.Pattern;

import static enkan.web.util.HttpResponseUtils.getHeader;
import static enkan.web.util.HttpResponseUtils.header;

/**
 * Sets {@code Cache-Control} response headers based on configurable rules.
 *
 * <p>A {@code staticPattern} regex distinguishes static assets from dynamic responses.
 * URIs matching the pattern receive the {@code staticDirective}; all others receive
 * the {@code dynamicDirective}. If a downstream handler already set {@code Cache-Control},
 * this middleware leaves it unchanged.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CacheControlMiddleware cache = new CacheControlMiddleware();
 * cache.setStaticPattern(Pattern.compile("^/assets/"));
 * cache.setStaticMaxAge(Duration.ofDays(365));
 * cache.setDynamicDirective("no-cache");
 * app.use(cache);
 * }</pre>
 *
 * @author kawasima
 */
@Middleware(name = "cacheControl")
public class CacheControlMiddleware implements WebMiddleware {
    private Pattern staticPattern;
    private String staticDirective = "public, max-age=31536000, immutable";
    private String dynamicDirective = "no-cache";

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        HttpResponse response = castToHttpResponse(chain.next(request));
        if (response == null) return null;

        if (getHeader(response, "Cache-Control") != null) return response;

        String uri = request.getUri();
        if (staticPattern != null && uri != null && staticPattern.matcher(uri).find()) {
            header(response, "Cache-Control", staticDirective);
        } else if (dynamicDirective != null) {
            header(response, "Cache-Control", dynamicDirective);
        }

        return response;
    }

    /**
     * Sets the regex pattern that identifies static asset URIs.
     * Uses {@link java.util.regex.Matcher#find()}, so {@code ^/assets/} matches
     * without needing to cover the full URI.
     *
     * @param staticPattern pattern to match static asset paths, or {@code null} to disable
     */
    public void setStaticPattern(Pattern staticPattern) {
        this.staticPattern = staticPattern;
    }

    /**
     * Convenience setter that builds a {@code public, max-age=N, immutable} directive
     * from a {@link Duration}.
     *
     * @param duration cache lifetime for static assets
     */
    public void setStaticMaxAge(Duration duration) {
        this.staticDirective = "public, max-age=" + duration.toSeconds() + ", immutable";
    }

    /**
     * Sets the full {@code Cache-Control} directive for static assets.
     * Overrides any value set by {@link #setStaticMaxAge(Duration)}.
     *
     * @param staticDirective directive string, e.g. {@code "public, max-age=3600, must-revalidate"}
     */
    public void setStaticDirective(String staticDirective) {
        this.staticDirective = staticDirective;
    }

    /**
     * Sets the {@code Cache-Control} directive for non-static responses.
     * Pass {@code null} to omit the header for non-static URIs.
     *
     * @param dynamicDirective directive string, e.g. {@code "no-cache"}, or {@code null}
     */
    public void setDynamicDirective(String dynamicDirective) {
        this.dynamicDirective = dynamicDirective;
    }
}
