package enkan.web.application;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebApplicationTest {

    @Test
    void emptyMiddlewareStackThrowsOnHandle() {
        WebApplication app = new WebApplication();
        HttpRequest request = builder(new DefaultHttpRequest()).build();
        assertThatThrownBy(() -> app.handle(request))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void singleMiddlewareIsInvoked() {
        WebApplication app = new WebApplication();
        app.use(new AnyPredicate<>(), "test", new Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                return HttpResponse.of("single");
            }
        });

        HttpRequest request = builder(new DefaultHttpRequest()).build();
        HttpResponse response = app.handle(request);

        assertThat(response).isNotNull();
        assertThat(response.getBodyAsString()).isEqualTo("single");
    }

    @Test
    void multipleMiddlewaresAreChainedInOrder() {
        WebApplication app = new WebApplication();
        List<String> order = new ArrayList<>();

        app.use(new AnyPredicate<>(), "first", new Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                order.add("first");
                return (HttpResponse) chain.next(req);
            }
        });

        app.use(new AnyPredicate<>(), "second", new Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                order.add("second");
                return HttpResponse.of("done");
            }
        });

        HttpRequest request = builder(new DefaultHttpRequest()).build();
        HttpResponse response = app.handle(request);

        assertThat(order).containsExactly("first", "second");
        assertThat(response.getBodyAsString()).isEqualTo("done");
    }

    @Test
    void createRequestReturnsNonNullHttpRequest() {
        WebApplication app = new WebApplication();
        HttpRequest request = app.createRequest();
        assertThat(request).isNotNull();
        assertThat(request).isInstanceOf(HttpRequest.class);
    }

    @Test
    void getMiddlewareStackReflectsRegisteredMiddleware() {
        WebApplication app = new WebApplication();
        assertThat(app.getMiddlewareStack()).isEmpty();

        app.use(new AnyPredicate<>(), "m1", new Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                return HttpResponse.of("ok");
            }
        });

        assertThat(app.getMiddlewareStack()).hasSize(1);

        app.use(new AnyPredicate<>(), "m2", new Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                return HttpResponse.of("ok");
            }
        });

        assertThat(app.getMiddlewareStack()).hasSize(2);
    }
}
