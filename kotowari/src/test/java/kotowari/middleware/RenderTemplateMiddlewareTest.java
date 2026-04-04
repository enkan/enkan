package kotowari.middleware;

import enkan.Endpoint;
import enkan.chain.DefaultMiddlewareChain;
import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.security.UserPrincipal;
import enkan.system.inject.ComponentInjector;
import enkan.util.Predicates;
import kotowari.component.TemplateEngine;
import kotowari.data.TemplatedHttpResponse;
import kotowari.io.LazyRenderInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

class RenderTemplateMiddlewareTest {
    private RenderTemplateMiddleware sut;

    private static final Function<List<?>, Object> HAS_ANY_PERMISSIONS = arguments -> {
        if (arguments.size() >= 2) {
            Object principal = arguments.getFirst();
            if (principal instanceof UserPrincipal up) {
                return arguments.subList(1, arguments.size())
                        .stream()
                        .anyMatch(p -> up.hasPermission(Objects.toString(p)));
            } else {
                throw new MisconfigurationException("kotowari.HAS_PERMISSION_FIRST_ARG", "hasAnyPermission");
            }
        } else {
            throw new MisconfigurationException("kotowari.HAS_ANY_PERMISSION_WRONG_ARGS");
        }
    };

    @BeforeEach
    void setup() {
        sut = new RenderTemplateMiddleware();
    }

    @Test
    void hasAnyPermissions() {
        Object ret = HAS_ANY_PERMISSIONS.apply(Arrays.asList(
                new UserPrincipal() {
                    @Override
                    public boolean hasPermission(String permission) {
                        return Objects.equals("PERM1", permission);
                    }

                    @Override
                    public String getName() {
                        return "test";
                    }
                }
        , "PERM1"));
        assertThat(ret).isInstanceOf(Boolean.class).isEqualTo(true);
    }

    @Test
    void render() {
        final TemplatedHttpResponse response = TemplatedHttpResponse.create("template1");
        response.setBody("hello");
        sut.render(response);
        assertThat(response.getBodyAsString()).isEqualTo("hello");
    }

    private static class TestTemplateEngine extends TemplateEngine<TestTemplateEngine> {
        @Override
        protected ComponentLifecycle<TestTemplateEngine> lifecycle() {
            return new ComponentLifecycle<>() {
                @Override
                public void start(TestTemplateEngine component) {

                }

                @Override
                public void stop(TestTemplateEngine component) {

                }
            };
        }

        @Override
        public HttpResponse render(String name, Object... keyOrVals) {
            final TemplatedHttpResponse response = TemplatedHttpResponse.create("template1");
            response.setBody(new LazyRenderInputStream(() ->
                    new ByteArrayInputStream("hello".getBytes())));

            return response;
        }

        @Override
        public Object createFunction(Function<List<?>, Object> func) {
            return (Function<List<?>, Object>) arguments -> null;
        }
    }
    @Test
    void handle() {
        final HttpRequest request = new DefaultHttpRequest();
        Map<String, SystemComponent<?>> components = new HashMap<>();
        TemplateEngine<?> templateEngine = new TestTemplateEngine();
        components.put("templateEngine", templateEngine);
        ComponentInjector injector = new ComponentInjector(components);
        injector.inject(sut);

        final HttpResponse res = sut.handle(request, new DefaultMiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse>(Predicates.any(), "null",
                (Endpoint<HttpRequest, HttpResponse>) request1 -> templateEngine.render("template1")));
        assertThat(res.getBodyAsString()).isEqualTo("hello");
    }

}
