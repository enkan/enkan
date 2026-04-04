package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.ETagUtils;
import enkan.web.util.HttpDateFormat;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;


/**
 * Evaluates conditional request headers (RFC 9110 §13) and returns
 * 304 Not Modified or 412 Precondition Failed when appropriate.
 *
 * <p>This middleware runs after the downstream handler produces a response.
 * It auto-generates a weak ETag for {@link String} and {@code byte[]} bodies
 * when no ETag header is already present. The evaluation follows the
 * precedence order defined in RFC 9110 §13.2.2.
 *
 * <p>If-Range (§13.1.5) is not evaluated because enkan does not support
 * Range requests.
 *
 * @author kawasima
 */
@Middleware(name = "conditional")
public class ConditionalMiddleware implements WebMiddleware {

    /** Headers to preserve in 304 responses (RFC 9110 §15.4.5). */
    private static final Set<String> NOT_MODIFIED_HEADERS = Set.of(
            "etag", "date", "vary", "cache-control", "expires", "content-location");

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> next) {
        String method = request.getRequestMethod();

        // §13.2.1: Skip for CONNECT, OPTIONS, TRACE
        if ("CONNECT".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method)) {
            return castToHttpResponse(next.next(request));
        }

        HttpResponse response = castToHttpResponse(next.next(request));

        // §13.2.1: Only evaluate for 2xx responses
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return response;
        }

        // Auto-generate weak ETag if not present (String/byte[] body only)
        String etag = response.getHeaders().get("ETag");
        if (etag == null) {
            String contentEncoding = response.getHeaders().get("Content-Encoding");
            etag = ETagUtils.generateWeakETag(response.getBody(), contentEncoding);
            if (etag != null) {
                response.getHeaders().put("ETag", etag);
            }
        }

        // §13.2.2 Step 1: If-Match (strong comparison)
        String ifMatch = request.getHeaders().get("If-Match");
        if (ifMatch != null) {
            if (!ETagUtils.matchesHeader(ifMatch, etag, false)) {
                return preconditionFailed();
            }
            // condition true → proceed to step 3
        }

        // §13.2.2 Step 2: If-Unmodified-Since (only when no If-Match)
        if (ifMatch == null) {
            String ifUnmodifiedSince = request.getHeaders().get("If-Unmodified-Since");
            if (isModifiedAfter(ifUnmodifiedSince, response)) {
                return preconditionFailed();
            }
        }

        // §13.2.2 Step 3: If-None-Match (weak comparison)
        String ifNoneMatch = request.getHeaders().get("If-None-Match");
        if (ifNoneMatch != null) {
            if (ETagUtils.matchesHeader(ifNoneMatch, etag, true)) {
                if ("GET".equals(method) || "HEAD".equals(method)) {
                    return notModified(response);
                } else {
                    return preconditionFailed();
                }
            }
        }

        // §13.2.2 Step 4: If-Modified-Since (GET/HEAD only, no If-None-Match)
        if (ifNoneMatch == null && ("GET".equals(method) || "HEAD".equals(method))) {
            String ifModifiedSince = request.getHeaders().get("If-Modified-Since");
            if (isNotModifiedSince(ifModifiedSince, response)) {
                return notModified(response);
            }
        }

        // Step 5 (If-Range) skipped — no Range support
        // Step 6: proceed normally
        return response;
    }

    /**
     * Checks whether the response's Last-Modified date is after the given condition date.
     * Returns false if either date is null or unparseable.
     */
    private boolean isModifiedAfter(String conditionDate, HttpResponse response) {
        if (conditionDate == null) return false;
        Optional<Instant> condInstant = HttpDateFormat.parse(conditionDate);
        Optional<Instant> modInstant = parseLastModified(response);
        return condInstant.isPresent() && modInstant.isPresent()
                && modInstant.get().isAfter(condInstant.get());
    }

    /**
     * Checks whether the response has NOT been modified since the given condition date.
     * Returns false if either date is null or unparseable (condition is skipped per §13.1.3).
     */
    private boolean isNotModifiedSince(String conditionDate, HttpResponse response) {
        if (conditionDate == null) return false;
        Optional<Instant> condInstant = HttpDateFormat.parse(conditionDate);
        Optional<Instant> modInstant = parseLastModified(response);
        return condInstant.isPresent() && modInstant.isPresent()
                && !modInstant.get().isAfter(condInstant.get());
    }

    private Optional<Instant> parseLastModified(HttpResponse response) {
        String lastModified = response.getHeaders().get("Last-Modified");
        return lastModified != null ? HttpDateFormat.parse(lastModified) : Optional.empty();
    }

    private HttpResponse notModified(HttpResponse original) {
        HttpResponse response = HttpResponse.of("");
        response.setStatus(304);
        original.getHeaders().keySet().forEach(name -> {
            if (NOT_MODIFIED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                response.getHeaders().put(name, original.getHeaders().get(name));
            }
        });
        response.setBody((String) null);
        return response;
    }

    private HttpResponse preconditionFailed() {
        HttpResponse response = HttpResponse.of("");
        response.setStatus(412);
        response.setBody((String) null);
        return response;
    }
}
