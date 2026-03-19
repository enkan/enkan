package kotowari.routing;

import enkan.collection.OptionMap;
import kotowari.routing.controller.ExampleController;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for individual Route behavior.
 */
class RouteTest {

    // A second controller class to distinguish from ExampleController
    public static class AnotherController {
        public String index() {
            return "another";
        }
    }

    @Test
    void recognizeMatchesSimpleStaticRoute() {
        Routes routes = Routes.define(r ->
                r.get("/hello").to(ExampleController.class, "method1")
        ).compile();

        // Use recognizePath to go through the route list; also test Route.recognize(path, method) indirectly
        OptionMap result = routes.recognizePath(
                buildRequest("/hello", "GET"));
        assertThat(result).isNotNull();
        assertThat(result.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(result.get("action")).isEqualTo("method1");
    }

    @Test
    void recognizeReturnsNullForNonMatchingPath() {
        Routes routes = Routes.define(r ->
                r.get("/hello").to(ExampleController.class, "method1")
        ).compile();

        OptionMap result = routes.recognizePath(
                buildRequest("/goodbye", "GET"));
        assertThat(result).isEmpty();
    }

    @Test
    void recognizeExtractsDynamicParameters() {
        Routes routes = Routes.define(r ->
                r.get("/users/:id").to(ExampleController.class, "method1")
        ).compile();

        OptionMap result = routes.recognizePath(
                buildRequest("/users/42", "GET"));
        assertThat(result).isNotNull();
        assertThat(result.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(result.get("id")).isEqualTo("42");
    }

    @Test
    void matchesControllerReturnsTrueForCorrectController() {
        Routes routes = Routes.define(r ->
                r.get("/test").to(ExampleController.class, "method1")
        ).compile();

        // generate uses matchesController internally; test it via generate
        String path = routes.generate(OptionMap.of(
                "controller", ExampleController.class,
                "action", "method1"));
        assertThat(path).isEqualTo("/test");
    }

    @Test
    void matchesControllerReturnsFalseForWrongController() {
        Routes routes = Routes.define(r ->
                r.get("/test").to(ExampleController.class, "method1")
        ).compile();

        // generate should fail when controller does not match any route
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                routes.generate(OptionMap.of(
                        "controller", AnotherController.class,
                        "action", "index"))
        ).isInstanceOf(Exception.class);
    }

    @Test
    void matchesControllerAndActionChecksBothControllerAndAction() {
        Routes routes = Routes.define(r -> {
            r.get("/m1").to(ExampleController.class, "method1");
            r.get("/m2").to(ExampleController.class, "method2");
        }).compile();

        // Correct controller + action "method2" should generate /m2
        String path = routes.generate(OptionMap.of(
                "controller", ExampleController.class,
                "action", "method2"));
        assertThat(path).isEqualTo("/m2");
    }

    @Test
    void constraintsAppliedThroughRecognize() {
        // Constraints are set internally by the route builder (controller, action).
        // Verify constraints are reflected in the recognized result.
        Routes routes = Routes.define(r ->
                r.get("/items/:id").to(ExampleController.class, "method3")
        ).compile();

        OptionMap result = routes.recognizePath(
                buildRequest("/items/99", "GET"));
        assertThat(result.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(result.get("action")).isEqualTo("method3");
        assertThat(result.get("id")).isEqualTo("99");
    }

    @Test
    void generateBuildsUrlFromOptions() {
        Routes routes = Routes.define(r ->
                r.get("/users/:id").to(ExampleController.class, "method1")
        ).compile();

        String path = routes.generate(OptionMap.of(
                "controller", ExampleController.class,
                "action", "method1",
                "id", "123"));
        assertThat(path).isEqualTo("/users/123");
    }

    @Test
    void methodConditionFilteringGetOnlyRouteRejectsPost() {
        Routes routes = Routes.define(r ->
                r.get("/only-get").to(ExampleController.class, "method1")
        ).compile();

        OptionMap getResult = routes.recognizePath(
                buildRequest("/only-get", "GET"));
        assertThat(getResult).isNotEmpty();
        assertThat(getResult.get("action")).isEqualTo("method1");

        OptionMap postResult = routes.recognizePath(
                buildRequest("/only-get", "POST"));
        assertThat(postResult).isEmpty();
    }

    private static enkan.data.HttpRequest buildRequest(String uri, String method) {
        return enkan.util.BeanBuilder.builder(new enkan.data.DefaultHttpRequest())
                .set(enkan.data.HttpRequest::setUri, uri)
                .set(enkan.data.HttpRequest::setRequestMethod, method)
                .build();
    }
}
