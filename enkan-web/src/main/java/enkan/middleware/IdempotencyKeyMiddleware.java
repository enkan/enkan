package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Headers;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.middleware.idempotency.IdempotencyEntry;
import enkan.middleware.session.KeyValueStore;
import enkan.util.sf.SfItem;
import enkan.util.sf.SfParseException;
import enkan.util.sf.SfValue.SfString;
import enkan.util.sf.StructuredFields;

import enkan.exception.MisconfigurationException;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Set;

import static enkan.util.BeanBuilder.builder;

/**
 * Middleware that provides idempotency for non-idempotent HTTP methods (POST, PATCH)
 * using the {@code Idempotency-Key} request header.
 *
 * <p>When a client sends a request with an {@code Idempotency-Key} header, this middleware:
 * <ul>
 *   <li>Executes the request and caches the response for the first occurrence of a key</li>
 *   <li>Returns the cached response for subsequent requests with the same key</li>
 *   <li>Returns {@code 409 Conflict} if a request with the same key is still in-flight</li>
 * </ul>
 *
 * <p>Requires a {@link KeyValueStore} to be injected via the enkan component system.
 * The TTL for idempotency entries is determined by the store implementation
 * (e.g., {@link enkan.middleware.session.MemoryStore#setTtlSeconds(long)}).
 * A dedicated store instance with appropriate TTL (typically 24 hours) is recommended.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * app.use(new IdempotencyKeyMiddleware());
 * }</pre>
 *
 * @author kawasima
 * @see <a href="https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/">
 *      draft-ietf-httpapi-idempotency-key-header</a>
 */
@Middleware(name = "idempotencyKey")
public class IdempotencyKeyMiddleware implements WebMiddleware {

    private static final String KEY_PREFIX = "idempotency:";
    private static final int MAX_KEY_LENGTH = 256;

    @Inject
    private KeyValueStore store;

    private Set<String> methods = Set.of("POST", "PATCH");

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        if (store == null || !isTargetMethod(request)) {
            return castToHttpResponse(chain.next(request));
        }

        String key = extractKey(request);
        if (key == null) {
            return castToHttpResponse(chain.next(request));
        }

        // Scope the store key by method + URI to prevent cross-endpoint collisions
        String storeKey = KEY_PREFIX + request.getRequestMethod() + ":" + request.getUri() + ":" + key;

        // Atomically claim the key. If another request already owns it,
        // re-read until we observe a stable state so that a completed
        // response is replayed instead of incorrectly returning 409.
        while (true) {
            if (store.putIfAbsent(storeKey, IdempotencyEntry.inFlight())) {
                return executeRequest(storeKey, request, chain);
            }

            Object raw = store.read(storeKey);
            if (raw == null) {
                // Entry expired or was evicted between putIfAbsent and read — retry
                continue;
            }
            if (!(raw instanceof IdempotencyEntry existing)) {
                // Unexpected data in store — overwrite
                store.delete(storeKey);
                continue;
            }

            return switch (existing.state()) {
                case IN_FLIGHT -> conflictResponse();
                case COMPLETED -> existing.toResponse();
            };
        }
    }

    private <NNREQ, NNRES> HttpResponse executeRequest(String storeKey, HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        try {
            HttpResponse response = castToHttpResponse(chain.next(request));
            if (response == null) {
                store.delete(storeKey);
                return null;
            }
            store.write(storeKey, IdempotencyEntry.completed(response));
            return response;
        } catch (Throwable t) {
            store.delete(storeKey);
            throw t;
        }
    }

    private boolean isTargetMethod(HttpRequest request) {
        String method = request.getRequestMethod();
        return method != null && methods.contains(method.toUpperCase(Locale.ENGLISH));
    }

    private String extractKey(HttpRequest request) {
        String raw = request.getHeaders().get("Idempotency-Key");
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            SfItem item = StructuredFields.parseItem(raw);
            if (item.value() instanceof SfString s) {
                String key = s.value();
                if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
                    return null;
                }
                return key;
            }
            return null;
        } catch (SfParseException e) {
            return null;
        }
    }

    private HttpResponse conflictResponse() {
        return builder(HttpResponse.of("A request with this Idempotency-Key is already being processed"))
                .set(HttpResponse::setStatus, 409)
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                .build();
    }

    /**
     * Sets the HTTP methods that require idempotency key processing.
     *
     * @param methods the set of HTTP methods (e.g., "POST", "PATCH")
     */
    public void setMethods(Set<String> methods) {
        this.methods = methods.stream()
                .map(m -> m.toUpperCase(Locale.ENGLISH))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Sets the key-value store for idempotency entries.
     *
     * @param store the store to use
     */
    public void setStore(KeyValueStore store) {
        if (store == null) {
            throw new MisconfigurationException("core.NULL_ARGUMENT", "store");
        }
        this.store = store;
    }
}
