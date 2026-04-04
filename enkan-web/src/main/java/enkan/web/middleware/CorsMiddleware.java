package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.collection.Headers;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static enkan.util.BeanBuilder.*;
import static enkan.web.util.HttpResponseUtils.*;
import static enkan.util.ThreadingUtils.*;

/**
 * CORS setting.
 *
 * @author syobochim
 */
@Middleware(name = "cors")
public class CorsMiddleware implements WebMiddleware {
    private static final Logger LOG = Logger.getLogger(CorsMiddleware.class.getName());
    private final AtomicBoolean misconfigurationWarned = new AtomicBoolean(false);
    private Set<String> methods;
    private Set<String> origins;
    private Set<String> headers;
    private Long maxAge;
    private boolean credentials;

    public CorsMiddleware() {
        methods = Set.of("GET", "POST", "DELETE", "PUT", "PATCH", "HEAD", "OPTIONS");
        origins = Set.of("*");
        headers = Set.of(
                "Origin", "Accept", "X-Requested-With", "Content-Type",
                "Access-Control-Request-Method", "Access-Control-Request-Headers");
        maxAge = 1800L;
        credentials = true;
    }

    /** {@inheritDoc} Applies CORS headers to the response based on the request Origin. */
    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        if (credentials && isAnyOriginAllowed() && misconfigurationWarned.compareAndSet(false, true)) {
            LOG.warning("CorsMiddleware: credentials=true with origins=[\"*\"] is invalid per CORS spec. " +
                    "Browsers will reject such responses. Set explicit allowed origins instead.");
        }
        if (isCORSRequest(request)) {
            if (!isOriginAllowed(request) || methods.stream().noneMatch(x -> x.equalsIgnoreCase(request.getRequestMethod()))) {
                return invalidCors(request);
            }
            if (isPreflightRequest(request)) {
                Headers responseHeaders = Headers.empty();
                if (isAnyOriginAllowed()) {
                    responseHeaders.put("Access-Control-Allow-Origin", "*");
                } else {
                    String origin = some(request.getHeaders(),
                            headers -> headers.get("origin"))
                            .orElse("*");
                    responseHeaders.put("Access-Control-Allow-Origin", origin);
                    // Vary: Origin ensures proxies cache separate preflight responses per origin.
                    responseHeaders.put("Vary", "Origin");
                }

                if (methods != null && !methods.isEmpty()) {
                    responseHeaders.put("Access-Control-Allow-Methods", String.join(", ", methods));
                }
                if (headers != null && !headers.isEmpty()) {
                    responseHeaders.put("Access-Control-Allow-Headers", String.join(", ", headers));
                }
                if (credentials) {
                    responseHeaders.put("Access-Control-Allow-Credentials", "true");
                }
                if (maxAge != null && maxAge > 0L) {
                    responseHeaders.put("Access-Control-Max-Age", String.valueOf(maxAge));
                }
                return builder(HttpResponse.of(""))
                        .set(HttpResponse::setStatus, 200)
                        .set(HttpResponse::setHeaders, responseHeaders)
                        .build();
            }
        }

        HttpResponse response = castToHttpResponse(chain.next(request));

        if (isCORSRequest(request)) {
            String requestOrigin = some(request.getHeaders(), h -> h.get("origin")).orElse(null);
            if (isAnyOriginAllowed()) {
                header(response, "Access-Control-Allow-Origin", "*");
            } else if (requestOrigin != null && origins.contains(requestOrigin)) {
                // RFC 6454 §7.2: Access-Control-Allow-Origin must be a single origin, not a list.
                // Echo back the matched request origin and add Vary: Origin for correct caching.
                header(response, "Access-Control-Allow-Origin", requestOrigin);
                appendVaryOrigin(response);
            }
            if (credentials) {
                header(response, "Access-Control-Allow-Credentials", "true");
            }
        }
        return response;
    }

    private HttpResponse invalidCors(HttpRequest request) {
        return builder(HttpResponse.of("Invalid CORS request; Origin="
                + request.getHeaders().get("origin")
                + ", Method="
                + request.getRequestMethod()))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                .set(HttpResponse::setStatus, 403)
                .build();
    }

    private boolean isOriginAllowed(HttpRequest request) {
        return some(request.getHeaders(),
                headers -> headers.get("origin"),
                origin -> isAnyOriginAllowed() || origins.contains(origin))
                .orElse(false);
    }

    /**
     * Determines if any origin is allowed.
     *
     * @return true if any origin is allowed
     */
    private boolean isAnyOriginAllowed() {
        return origins.contains("*");
    }

    private boolean isPreflightRequest(HttpRequest httpRequest) {
        return Objects.equals(httpRequest.getRequestMethod().toUpperCase(Locale.ENGLISH), "OPTIONS")
                && httpRequest.getHeaders().containsKey("Access-Control-Request-Method");
    }

    private boolean isCORSRequest(HttpRequest httpRequest) {
        return Objects.nonNull(httpRequest.getHeaders().get("Origin"));
    }

    /**
     * Appends "Origin" to the Vary header without overwriting existing values.
     */
    private void appendVaryOrigin(HttpResponse response) {
        String existing = getHeader(response, "Vary");
        if (existing == null) {
            header(response, "Vary", "Origin");
        } else if (Arrays.stream(existing.split(","))
                .map(String::trim)
                .noneMatch("Origin"::equalsIgnoreCase)) {
            header(response, "Vary", existing + ", Origin");
        }
    }

    /**
     * Set the allowed methods.
     *
     * @param methods A set of allowed methods
     */
    public void setMethods(Set<String> methods) {
        this.methods = Set.copyOf(methods);
    }

    /**
     * Set the allowed origins.
     *
     * @param origins A set of allowed origins
     */
    public void setOrigins(Set<String> origins) {
        this.origins = Set.copyOf(origins);
    }

    /**
     * Set the allowed headers.
     *
     * @param headers A set of allowed headers
     */
    public void setHeaders(Set<String> headers) {
        this.headers = Set.copyOf(headers);
    }

    /**
     * Set the max age.
     *
     * @param maxAge max age
     */
    public void setMaxAge(Long maxAge) {
        if (maxAge != null && maxAge < 0) {
            throw new MisconfigurationException("core.INVALID_ARGUMENT", "maxAge", maxAge);
        }
        this.maxAge = maxAge;
    }

    /**
     * Set the credentials.
     *
     * @param credentials credentials
     */
    public void setCredentials(boolean credentials) {
        this.credentials = credentials;
    }
}
