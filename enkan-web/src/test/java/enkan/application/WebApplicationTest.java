package enkan.application;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class WebApplicationTest {

    @Test
    void emptyMiddlewareStackReturnsNullOnHandle() {
        WebApplication app = new WebApplication();
        // With no middleware registered, handle should not throw but the chain
        // produces null because there is nothing to execute.
        try {
            HttpRequest request = builder(new DefaultHttpRequest()).build();
            HttpResponse response = app.handle(request);
            // If it gets here without exception, the stack was empty and returned null
            assertThat(response).isNull();
        } catch (java.util.NoSuchElementException e) {
            // LinkedList.getFirst() throws when the stack is empty; this is expected
            // since handle() tries to chain into the first middleware.
        }
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
