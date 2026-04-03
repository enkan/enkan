package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.middleware.idempotency.IdempotencyEntry;
import enkan.middleware.session.KeyValueStore;
import enkan.middleware.session.MemoryStore;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static enkan.util.BeanBuilder.builder;
import static enkan.util.HttpResponseUtils.getHeader;
import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyMiddlewareTest {

    private IdempotencyKeyMiddleware middleware;
    private KeyValueStore store;
    private AtomicInteger callCount;

    @BeforeEach
    void setup() {
        store = new MemoryStore();
        middleware = new IdempotencyKeyMiddleware();
        middleware.setStore(store);
        callCount = new AtomicInteger(0);
    }

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chainReturning(String body, int status) {
        return new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    callCount.incrementAndGet();
                    return builder(HttpResponse.of(body))
                            .set(HttpResponse::setStatus, status)
                            .set(HttpResponse::setHeaders, Headers.of("Content-Type", "application/json"))
                            .build();
                });
    }

    private HttpRequest postRequest(String idempotencyKey) {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "POST")
                .set(HttpRequest::setHeaders, Headers.of("Host", "example.com"))
                .set(HttpRequest::setScheme, "https")
                .set(HttpRequest::setUri, "/orders")
                .build();
        if (idempotencyKey != null) {
            request.getHeaders().put("Idempotency-Key", "\"" + idempotencyKey + "\"");
        }
        return request;
    }

    @Test
    void getRequestPassesThrough() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setHeaders, Headers.of("Host", "example.com"))
                .set(HttpRequest::setScheme, "https")
                .set(HttpRequest::setUri, "/orders")
                .build();
        request.getHeaders().put("Idempotency-Key", "\"some-key\"");

        HttpResponse response = middleware.handle(request, chainReturning("{}", 200));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void postWithoutKeyPassesThrough() {
        HttpRequest request = postRequest(null);

        HttpResponse response = middleware.handle(request, chainReturning("{\"id\":1}", 201));

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void firstRequestExecutesAndCaches() {
        HttpRequest request = postRequest("key-1");
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = chainReturning("{\"id\":1}", 201);

        HttpResponse response = middleware.handle(request, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo("{\"id\":1}");
        assertThat(callCount.get()).isEqualTo(1);
        // Verify entry was stored
        assertThat(store.read("key-1")).isNotNull();
    }

    @Test
    void retryReturnsCache() {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = chainReturning("{\"id\":1}", 201);

        // First request
        middleware.handle(postRequest("key-2"), chain);
        assertThat(callCount.get()).isEqualTo(1);

        // Retry with same key
        HttpResponse retryResponse = middleware.handle(postRequest("key-2"), chain);

        assertThat(retryResponse.getStatus()).isEqualTo(201);
        assertThat(retryResponse.getBody()).isEqualTo("{\"id\":1}");
        assertThat((String) getHeader(retryResponse, "Content-Type")).isEqualTo("application/json");
        // Handler was NOT called again
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void differentKeysAreIndependent() {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = chainReturning("{\"id\":1}", 201);

        middleware.handle(postRequest("key-a"), chain);
        middleware.handle(postRequest("key-b"), chain);

        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void inFlightReturns409() {
        // Manually write an IN_FLIGHT entry
        store.write("key-inflight", IdempotencyEntry.inFlight());

        HttpResponse response = middleware.handle(postRequest("key-inflight"),
                chainReturning("{}", 200));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(callCount.get()).isEqualTo(0);
    }

    @Test
    void patchMethodIsTargeted() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "PATCH")
                .set(HttpRequest::setHeaders, Headers.of("Host", "example.com"))
                .set(HttpRequest::setScheme, "https")
                .set(HttpRequest::setUri, "/orders/1")
                .build();
        request.getHeaders().put("Idempotency-Key", "\"patch-key\"");

        HttpResponse response = middleware.handle(request, chainReturning("{}", 200));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(callCount.get()).isEqualTo(1);
        assertThat(store.read("patch-key")).isNotNull();
    }

    @Test
    void exceptionCleansUpInFlightEntry() {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(
                new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    throw new RuntimeException("handler error");
                });

        try {
            middleware.handle(postRequest("key-error"), chain);
        } catch (RuntimeException ignored) {
        }

        // IN_FLIGHT entry should be cleaned up
        assertThat(store.read("key-error")).isNull();
    }

    @Test
    void invalidHeaderIsIgnored() {
        HttpRequest request = postRequest(null);
        // Set a malformed Idempotency-Key (not a valid SF Item)
        request.getHeaders().put("Idempotency-Key", "not-a-valid-sf-item{");

        HttpResponse response = middleware.handle(request, chainReturning("{}", 200));

        // Should pass through without idempotency processing
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(callCount.get()).isEqualTo(1);
    }
}
