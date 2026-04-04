package enkan.middleware.devel;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Test helper: wraps a Function as a terminal MiddlewareChain.
 */
class TestMiddlewareChain implements MiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> {

    private final Function<HttpRequest, HttpResponse> fn;

    TestMiddlewareChain(Function<HttpRequest, HttpResponse> fn) {
        this.fn = fn;
    }

    @Override
    public MiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> setNext(
            MiddlewareChain<HttpRequest, HttpResponse, ?, ?> next) {
        return this;
    }

    @Override
    public Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse> getMiddleware() {
        return null;
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public Predicate<? super HttpRequest> getPredicate() {
        return req -> true;
    }

    @Override
    public void setPredicate(Predicate<? super HttpRequest> predicate) {
    }

    @Override
    public HttpResponse next(HttpRequest req) {
        return fn.apply(req);
    }
}
