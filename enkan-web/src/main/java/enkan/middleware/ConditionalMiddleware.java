package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.util.ETagUtils;
import enkan.util.HttpDateFormat;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static enkan.util.BeanBuilder.builder;

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
            if (ifUnmodifiedSince != null) {
                String lastModified = response.getHeaders().get("Last-Modified");
                if (lastModified != null) {
                    Optional<Instant> condDate = HttpDateFormat.parse(ifUnmodifiedSince);
                    Optional<Instant> modDate = HttpDateFormat.parse(lastModified);
                    if (condDate.isPresent() && modDate.isPresent()
                            && modDate.get().isAfter(condDate.get())) {
                        return preconditionFailed();
                    }
                }
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
            if (ifModifiedSince != null) {
                String lastModified = response.getHeaders().get("Last-Modified");
                if (lastModified != null) {
                    Optional<Instant> condDate = HttpDateFormat.parse(ifModifiedSince);
                    Optional<Instant> modDate = HttpDateFormat.parse(lastModified);
                    if (condDate.isPresent() && modDate.isPresent()
                            && !modDate.get().isAfter(condDate.get())) {
                        return notModified(response);
                    }
                }
            }
        }

        // Step 5 (If-Range) skipped — no Range support
        // Step 6: proceed normally
        return response;
    }

    private HttpResponse notModified(HttpResponse original) {
        HttpResponse response = builder(HttpResponse.of(""))
                .set(HttpResponse::setStatus, 304)
                .build();
        // §15.4.5: preserve specific headers
        original.getHeaders().keySet().forEach(name -> {
            if (NOT_MODIFIED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                response.getHeaders().put(name, original.getHeaders().get(name));
            }
        });
        response.setBody((String) null);
        return response;
    }

    private HttpResponse preconditionFailed() {
        return builder(HttpResponse.of(""))
                .set(HttpResponse::setStatus, 412)
                .build();
    }
}
