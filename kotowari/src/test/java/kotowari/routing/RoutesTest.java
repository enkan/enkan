package kotowari.routing;

import enkan.web.collection.Headers;
import enkan.collection.OptionMap;
import enkan.web.data.ContentNegotiable;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.util.CodecUtils;
import enkan.util.MixinUtils;
import kotowari.routing.controller.ExampleController;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;

import static enkan.util.BeanBuilder.*;
import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
class RoutesTest {
    @Test
    void simple() {
        Routes routes = Routes.define(r -> r.get("/").to(ExampleController.class, "method1")).compile();

        OptionMap m = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(m.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(m.get("action")).isEqualTo("method1");
    }

    @Test
    void scope() {
        Routes routes = Routes.define(r -> {
            r.get("/home6").to(ExampleController.class, "method6");
            r.scope("/admin", admin -> admin.get("/list").to(ExampleController.class, "method1"));
        }).compile();

        OptionMap m = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/admin/list")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(m.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(m.get("action")).isEqualTo("method1");

        m = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/home6")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(m.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(m.get("action")).isEqualTo("method6");
    }

    @Test
    public void nestedScope() {
        Routes routes = Routes.define(r ->
                r.scope("/admin", admin ->
                        admin.scope("/user", user ->
                                user.get("/list").to(ExampleController.class, "method1")))
        ).compile();

        OptionMap m = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/admin/user/list")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(m.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(m.get("action")).isEqualTo("method1");
    }

    @Test
    void recognizeUtf8Dynamic() {
        Routes routes = Routes.define(r -> {
            r.get("/:val1").to(ExampleController.class, "method1");
            r.get("/*glob").to(ExampleController.class, "method2");
        }).compile();

        OptionMap m = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/" + CodecUtils.urlEncode("あいう"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(m.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(m.get("action")).isEqualTo("method1");
        assertThat(m.get("val1")).isEqualTo("あいう");
    }

    @Test
    void recognizeUtf8Path() {
        Routes routes = Routes.define(r -> {
            r.get("/:val1").to(ExampleController.class, "method1");
            r.get("/*glob").to(ExampleController.class, "method2");
        }).compile();

        OptionMap m = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/" + CodecUtils.urlEncode("あいう") +
                        "/" + CodecUtils.urlEncode("かきく"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(m.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(m.get("action")).isEqualTo("method2");
        assertThat(m.get("glob")).isEqualTo("あいう/かきく");
    }

    @Test
    public void invalidUtf8() {
        Routes routes = Routes.define(r -> r.get("/:val1").to(ExampleController.class, "method1")).compile();

        OptionMap m = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/%RR%01%01")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(m.get("controller")).isEqualTo(ExampleController.class.getName());
        assertThat(m.get("action")).isEqualTo("method1");
        assertThat(m.get("val1")).isEqualTo("%RR%01%01");
    }

    @Test
    void consume() {
        Routes routes = Routes.define(r ->
                r.get("/api").consumes(MediaType.APPLICATION_JSON_TYPE)
                        .to(ExampleController.class, "method1")
        ).compile();

        OptionMap notFound = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/api")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(notFound).isEmpty();

        OptionMap found = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/api")
                .set(HttpRequest::setHeaders, Headers.of("content-type", "application/json"))
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(found.get("action")).isEqualTo("method1");
    }

    @Test
    void produceNoAccept() {
        Routes routes = Routes.define(r ->
                r.get("/api").produces(MediaType.APPLICATION_JSON_TYPE)
                        .to(ExampleController.class, "method1")
        ).compile();

        OptionMap found = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/api")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(found.get("action"))
                .as("If there is no accept header, all requests can be produced.")
                .isEqualTo("method1");
    }

    @Test
    void cannotProduce() {
        Routes routes = Routes.define(r ->
                r.get("/api").produces(MediaType.APPLICATION_JSON_TYPE)
                        .to(ExampleController.class, "method1")
        ).compile();

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/api")
                .set(HttpRequest::setRequestMethod, "GET")
                .build();
        request = MixinUtils.mixin(request, ContentNegotiable.class);
        ((ContentNegotiable) request).setMediaType(MediaType.APPLICATION_XML_TYPE);
        OptionMap notFound = routes.recognizePath(request);
        assertThat(notFound).isEmpty();
    }

    @Test
    void produceWildcard() {
        Routes routes = Routes.define(r ->
                r.get("/api").produces(MediaType.WILDCARD_TYPE)
                        .to(ExampleController.class, "method1")
        ).compile();

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/api")
                .set(HttpRequest::setRequestMethod, "GET")
                .build();
        request = MixinUtils.mixin(request, ContentNegotiable.class);
        ((ContentNegotiable) request).setMediaType(MediaType.APPLICATION_XML_TYPE);
        OptionMap found = routes.recognizePath(request);
        assertThat(found).isNotEmpty();
        assertThat(found.get("action")).isEqualTo("method1");
    }

    @Test
    void concat() {
        final Routes routes = Routes.define(r -> {
            r.get("/a1").to(ExampleController.class);
            r.get("/a2").to(ExampleController.class);
        }).compile();

        final Routes another = Routes.define(r -> {
            r.get("/an1").to(ExampleController.class, "an1");
            r.get("/an2").to(ExampleController.class, "an2");
        }).compile();

        routes.concat(another);

        final OptionMap an2 = routes.recognizePath(builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/an2")
                .set(HttpRequest::setRequestMethod, "GET")
                .build());
        assertThat(an2.get("action")).isEqualTo("an2");
    }

}
