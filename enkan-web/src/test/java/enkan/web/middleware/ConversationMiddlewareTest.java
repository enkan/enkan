package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Parameters;
import enkan.component.builtin.HmacEncoder;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static enkan.util.Predicates.any;
import static enkan.util.ReflectionUtils.tryReflection;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationMiddlewareTest {
    private ConversationMiddleware middleware;
    private HmacEncoder hmacEncoder;

    private static final Endpoint<HttpRequest, HttpResponse> OK_ENDPOINT =
            req -> HttpResponse.of("ok");

    @BeforeEach
    void setup() {
        hmacEncoder = new HmacEncoder();
        middleware = new ConversationMiddleware();
        tryReflection(() -> {
            Field f = ConversationMiddleware.class.getDeclaredField("hmacEncoder");
            f.setAccessible(true);
            f.set(middleware, hmacEncoder);
            return null;
        });
    }

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> okChain() {
        return new DefaultMiddlewareChain<>(any(), null, OK_ENDPOINT);
    }

    private HttpRequest postRequestWithToken(String token) {
        HttpRequest request = new DefaultHttpRequest();
        request.setRequestMethod("POST");
        Parameters formParams = Parameters.empty();
        if (token != null) {
            formParams.put("__conversation-token", token);
        }
        request.setFormParams(formParams);
        return request;
    }

    @Test
    void getRequestPassesThroughWithoutToken() {
        HttpRequest request = new DefaultHttpRequest();
        request.setRequestMethod("GET");

        HttpResponse response = middleware.handle(request, okChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void postWithValidNonExpiryTokenIsAccepted() {
        // expires = -1 means no expiry
        String id = "conv-id-1";
        long expires = -1L;
        String hash = hmacEncoder.encodeToHex(id + "$" + expires);
        String token = id + "$" + hash + "$" + expires;

        HttpResponse response = middleware.handle(postRequestWithToken(token), okChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void postWithFutureExpiryTokenIsAccepted() {
        String id = "conv-id-2";
        long expires = System.currentTimeMillis() + 60_000L; // 1 minute ahead
        String hash = hmacEncoder.encodeToHex(id + "$" + expires);
        String token = id + "$" + hash + "$" + expires;

        HttpResponse response = middleware.handle(postRequestWithToken(token), okChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void postWithExpiredTokenIsRejected() {
        String id = "conv-id-3";
        long expires = System.currentTimeMillis() - 1L; // already expired
        String hash = hmacEncoder.encodeToHex(id + "$" + expires);
        String token = id + "$" + hash + "$" + expires;

        HttpResponse response = middleware.handle(postRequestWithToken(token), okChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void postWithNoTokenIsRejected() {
        HttpResponse response = middleware.handle(postRequestWithToken(null), okChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void postWithTamperedHashIsRejected() {
        String id = "conv-id-4";
        long expires = -1L;
        String token = id + "$TAMPERED$" + expires;

        HttpResponse response = middleware.handle(postRequestWithToken(token), okChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
