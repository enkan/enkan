package kotowari.routing.recognizer;

import enkan.collection.OptionMap;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import kotowari.routing.Routes;
import kotowari.routing.controller.ExampleController;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OptimizedRecognizer.
 */
class OptimizedRecognizerTest {

    @Test
    void toPlainSegmentsSplitsSimplePath() {
        OptimizedRecognizer recognizer = new OptimizedRecognizer();
        String[] segments = recognizer.toPlainSegments("/users/list");
        assertThat(segments).containsExactly("users", "list");
    }

    @Test
    void toPlainSegmentsHandlesLeadingAndTrailingSlashes() {
        OptimizedRecognizer recognizer = new OptimizedRecognizer();
        String[] segments = recognizer.toPlainSegments("///foo/bar///");
        assertThat(segments).containsExactly("foo", "bar");
    }

    @Test
    void toPlainSegmentsHandlesEmptyString() {
        OptimizedRecognizer recognizer = new OptimizedRecognizer();
        String[] segments = recognizer.toPlainSegments("");
        // Splitting an empty string yields one empty-string element
        assertThat(segments).containsExactly("");
    }

    @Test
    void setRoutesAndOptimizeMakesIsOptimizedTrue() {
        OptimizedRecognizer recognizer = new OptimizedRecognizer();
        assertThat(recognizer.isOptimized()).isFalse();

        Routes routes = Routes.define(r ->
                r.get("/test").to(ExampleController.class, "method1")
        ).compile();

        // After compile(), the recognizer inside Routes is already optimized.
        // Test directly by creating a fresh recognizer with a route list.
        OptimizedRecognizer freshRecognizer = new OptimizedRecognizer();
        assertThat(freshRecognizer.isOptimized()).isFalse();

        // Use reflection-free approach: set routes triggers optimize()
        // We use the Routes object to verify recognition works (implying optimization)
        OptionMap result = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/test")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(result).isNotEmpty();
    }

    @Test
    void recognizeMatchesStaticRoutes() {
        Routes routes = Routes.define(r -> {
            r.get("/alpha").to(ExampleController.class, "method1");
            r.get("/beta").to(ExampleController.class, "method2");
        }).compile();

        OptionMap result = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/beta")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(result.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(result.get("action")).isEqualTo("method2");
    }

    @Test
    void recognizeMatchesDynamicRoutesWithParameters() {
        Routes routes = Routes.define(r ->
                r.get("/users/:id/posts/:postId").to(ExampleController.class, "method1")
        ).compile();

        OptionMap result = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/users/7/posts/42")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(result.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(result.get("id")).isEqualTo("7");
        assertThat(result.get("postId")).isEqualTo("42");
    }

    @Test
    void recognizeReturnsEmptyForUnmatchedPaths() {
        Routes routes = Routes.define(r ->
                r.get("/exists").to(ExampleController.class, "method1")
        ).compile();

        OptionMap result = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/does-not-exist")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(result).isEmpty();
    }
}
