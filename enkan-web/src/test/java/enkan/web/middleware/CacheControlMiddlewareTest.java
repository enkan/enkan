package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.regex.Pattern;

import static enkan.util.BeanBuilder.builder;
import static enkan.web.util.HttpResponseUtils.getHeader;
import static enkan.web.util.HttpResponseUtils.header;
import static org.assertj.core.api.Assertions.assertThat;

class CacheControlMiddlewareTest {
    private HttpRequest request;
    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain;

    @BeforeEach
    void setup() {
        request = new DefaultHttpRequest();
        chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> builder(HttpResponse.of("hello")).build());
    }

    @Test
    void defaultDynamicDirectiveApplied() {
        request.setUri("/page");
        CacheControlMiddleware middleware = new CacheControlMiddleware();
        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Cache-Control")).isEqualTo("no-cache");
    }

    @Test
    void staticPatternMatchGetsStaticDirective() {
        request.setUri("/assets/app.js");
        CacheControlMiddleware middleware = new CacheControlMiddleware();
        middleware.setStaticPattern(Pattern.compile("^/assets/"));
        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Cache-Control"))
                .isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    void staticPatternMissGetsDynamicDirective() {
        request.setUri("/api/data");
        CacheControlMiddleware middleware = new CacheControlMiddleware();
        middleware.setStaticPattern(Pattern.compile("^/assets/"));
        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Cache-Control")).isEqualTo("no-cache");
    }

    @Test
    void existingHeaderIsNotOverwritten() {
        request.setUri("/page");
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> downstreamChain =
                new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                        (Endpoint<HttpRequest, HttpResponse>) req -> {
                            HttpResponse res = HttpResponse.of("hello");
                            header(res, "Cache-Control", "private, max-age=60");
                            return res;
                        });
        CacheControlMiddleware middleware = new CacheControlMiddleware();
        HttpResponse response = middleware.handle(request, downstreamChain);

        assertThat((String) getHeader(response, "Cache-Control")).isEqualTo("private, max-age=60");
    }

    @Test
    void setStaticMaxAgeBuildsDirective() {
        request.setUri("/assets/style.css");
        CacheControlMiddleware middleware = new CacheControlMiddleware();
        middleware.setStaticPattern(Pattern.compile("^/assets/"));
        middleware.setStaticMaxAge(Duration.ofHours(1));
        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Cache-Control"))
                .isEqualTo("public, max-age=3600, immutable");
    }

    @Test
    void nullDynamicDirectiveSkipsHeader() {
        request.setUri("/page");
        CacheControlMiddleware middleware = new CacheControlMiddleware();
        middleware.setDynamicDirective(null);
        HttpResponse response = middleware.handle(request, chain);

        assertThat(response.getHeaders().containsKey("Cache-Control")).isFalse();
    }

    @Test
    void customStaticDirectiveIsApplied() {
        request.setUri("/assets/logo.png");
        CacheControlMiddleware middleware = new CacheControlMiddleware();
        middleware.setStaticPattern(Pattern.compile("^/assets/"));
        middleware.setStaticDirective("public, max-age=600");
        HttpResponse response = middleware.handle(request, chain);

        assertThat((String) getHeader(response, "Cache-Control"))
                .isEqualTo("public, max-age=600");
    }
}
