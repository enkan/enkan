package kotowari.routing;

import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.CodecUtils;
import kotowari.routing.controller.ExampleController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
public class GenerateTest {
    @Test
    public void test() {
        Routes routes = Routes.define(r -> {
            r.get("/a/b/").to(TestController.class, "index");
            r.get("/a/b/:id").to(TestController.class, "show");
        }).compile();

        assertThat(routes.generate(OptionMap.of("controller", TestController.class, "action", "index")))
                .isEqualTo("/a/b/");
        assertThat(routes.generate(OptionMap.of("controller", TestController.class, "action", "show", "id", 1)))
                .isEqualTo("/a/b/1");
    }

    @Test
    public void generateWithStringController() {
        Routes routes = Routes.define(r -> {
            r.get("/a/b/").to(TestController.class, "index");
            r.get("/a/b/:id").to(TestController.class, "show");
        }).compile();

        assertThat(routes.generate(OptionMap.of("controller", TestController.class.getName(), "action", "index")))
                .isEqualTo("/a/b/");
        assertThat(routes.generate(OptionMap.of("controller", TestController.class.getName(), "action", "show", "id", 1)))
                .isEqualTo("/a/b/1");
    }

    @Test
    public void generateWithInvalidController() {
        Routes routes = Routes.define(r -> r.get("/a/b/").to(TestController.class, "index")).compile();

        assertThatThrownBy(() -> routes.generate(OptionMap.of("controller", 42, "action", "index")))
                .isInstanceOf(enkan.exception.MisconfigurationException.class);
        assertThatThrownBy(() -> routes.generate(OptionMap.of("controller", TestController.class.getName())))
                .isInstanceOf(enkan.exception.MisconfigurationException.class);
    }

    @Test
    public void generateUtf8Dynamic() {
        Routes routes = Routes.define(r -> {
            r.get("/:val1").to(ExampleController.class, "method1");
            r.get("/*glob").to(ExampleController.class, "method2");
        }).compile();

        String path = routes.generate(OptionMap.of(
                "controller", ExampleController.class,
                "action", "method1",
                "val1", "あいう"

        ));
        assertThat(path).isEqualTo("/" + CodecUtils.urlEncode("あいう"));

    }

    @Test
    public void generateUtf8Path() {
        Routes routes = Routes.define(r -> {
            r.get("/:val1").to(ExampleController.class, "method1");
            r.get("/*glob").to(ExampleController.class, "method2");
        }).compile();

        String path = routes.generate(OptionMap.of(
                "controller", ExampleController.class,
                "action", "method2",
                "glob", "あいう/かきく",
                "val2", "さしす"
        ));
        assertThat(path).isEqualTo("/" + CodecUtils.urlEncode("あいう")
                + "/" + CodecUtils.urlEncode("かきく")
                + "?val2=" + CodecUtils.urlEncode("さしす"));
    }

    public static class TestController {
        public HttpResponse index(HttpRequest request) {
            return null;
        }
        public HttpResponse show(HttpRequest request) {
            return null;
        }
    }
}
