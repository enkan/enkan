package kotowari.middleware;

import enkan.Endpoint;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.collection.Multimap;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.util.MixinUtils;
import enkan.util.Predicates;
import kotowari.data.BodyDeserializable;
import kotowari.data.Validatable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ValidateBodyMiddleware}.
 */
public class ValidateBodyMiddlewareTest {

    private ValidateBodyMiddleware<HttpResponse> middleware;

    @BeforeEach
    void setUp() {
        middleware = new ValidateBodyMiddleware<>();
    }

    private HttpRequest buildRequest() {
        HttpRequest request = new DefaultHttpRequest();
        request.setHeaders(Headers.empty());
        request.setRequestMethod("GET");
        return request;
    }

    private Endpoint<HttpRequest, HttpResponse> echoEndpoint() {
        return r -> HttpResponse.of("ok");
    }

    /**
     * When the request does not implement BodyDeserializable, the middleware
     * should simply pass through to the next chain without errors.
     */
    @Test
    void passThroughWhenRequestHasNoBody() {
        HttpRequest request = buildRequest();

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "test", echoEndpoint()));

        assertThat(response.getBodyAsString()).isEqualTo("ok");
    }

    /**
     * When the deserialized body is null, the middleware should pass through.
     */
    @Test
    void passThroughWhenDeserializedBodyIsNull() {
        HttpRequest request = MixinUtils.mixin(buildRequest(), BodyDeserializable.class);
        // body is not set, so getDeserializedBody() returns null

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "test", echoEndpoint()));

        assertThat(response.getBodyAsString()).isEqualTo("ok");
    }

    /**
     * When the deserialized body is not Validatable, the middleware should pass through.
     */
    @Test
    void passThroughWhenBodyIsNotValidatable() {
        HttpRequest request = MixinUtils.mixin(buildRequest(), BodyDeserializable.class);
        ((BodyDeserializable) request).setDeserializedBody("not-validatable");

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "test", echoEndpoint()));

        assertThat(response.getBodyAsString()).isEqualTo("ok");
    }

    /**
     * When the deserialized body implements Validatable and has no constraint
     * violations, the response status should remain as-is (200).
     */
    @Test
    void validFormPassesThroughWithoutErrors() {
        HttpRequest request = MixinUtils.mixin(buildRequest(), BodyDeserializable.class);
        ValidForm form = new ValidForm();
        ((BodyDeserializable) request).setDeserializedBody(form);

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "test", echoEndpoint()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(form.hasErrors()).isFalse();
    }

    /**
     * When validation produces constraint violations, the middleware sets
     * errors on the form and changes the response status to 400.
     */
    @Test
    void invalidFormSetsErrorsAndStatus400() {
        HttpRequest request = MixinUtils.mixin(buildRequest(), BodyDeserializable.class);
        InvalidForm form = new InvalidForm();
        ((BodyDeserializable) request).setDeserializedBody(form);

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "test", echoEndpoint()));

        assertThat(form.hasErrors()).isTrue();
        assertThat(form.getErrors()).isNotNull();
        assertThat(form.hasErrors("name")).isTrue();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    // --- test form classes ---

    /**
     * A valid form with no constraint violations.
     */
    public static class ValidForm implements Validatable {
        private final java.util.Map<String, Object> extensions = new java.util.HashMap<>();

        private String name = "valid";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getExtension(String name) {
            return (T) extensions.get(name);
        }

        @Override
        public <T> void setExtension(String name, T value) {
            extensions.put(name, value);
        }
    }

    /**
     * A form with a @NotNull constraint on a null field, triggering a violation.
     */
    public static class InvalidForm implements Validatable {
        private final java.util.Map<String, Object> extensions = new java.util.HashMap<>();

        @jakarta.validation.constraints.NotNull
        private String name = null;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getExtension(String name) {
            return (T) extensions.get(name);
        }

        @Override
        public <T> void setExtension(String name, T value) {
            extensions.put(name, value);
        }
    }
}
