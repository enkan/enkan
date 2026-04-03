package enkan.middleware.idempotency;

import enkan.collection.Headers;
import enkan.data.HttpResponse;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static enkan.util.BeanBuilder.builder;

/**
 * Stores the state and cached response for an idempotency key.
 *
 * @author kawasima
 */
public record IdempotencyEntry(State state, int status, Map<String, List<String>> headers, String body)
        implements Serializable {

    public enum State { IN_FLIGHT, COMPLETED }

    /**
     * Creates an IN_FLIGHT marker indicating the request is being processed.
     *
     * @return an in-flight entry
     */
    public static IdempotencyEntry inFlight() {
        return new IdempotencyEntry(State.IN_FLIGHT, 0, Map.of(), null);
    }

    /**
     * Creates a COMPLETED entry from an HTTP response.
     * Only String bodies are cached; other body types result in {@code null} body.
     *
     * @param response the HTTP response to cache
     * @return a completed entry
     */
    public static IdempotencyEntry completed(HttpResponse response) {
        int status = response.getStatus();
        Map<String, List<String>> hdrs = new LinkedHashMap<>();
        if (response.getHeaders() != null) {
            response.getHeaders().forEachHeader((name, value) ->
                    hdrs.computeIfAbsent(name, _ -> new ArrayList<>()).add(value.toString()));
        }
        Object responseBody = response.getBody();
        String body = responseBody instanceof String s ? s : null;
        return new IdempotencyEntry(State.COMPLETED, status, hdrs, body);
    }

    /**
     * Reconstructs an HTTP response from this cached entry.
     *
     * @return the reconstructed response
     */
    public HttpResponse toResponse() {
        Headers responseHeaders = Headers.empty();
        headers.forEach((name, values) -> values.forEach(v -> responseHeaders.put(name, v)));
        return builder(HttpResponse.of(body != null ? body : ""))
                .set(HttpResponse::setStatus, status)
                .set(HttpResponse::setHeaders, responseHeaders)
                .build();
    }

    /**
     * Returns whether the body was cached. If false, the response body
     * was not a String and the original request should be re-executed.
     *
     * @return true if the body was captured
     */
    public boolean hasBody() {
        return body != null;
    }
}
