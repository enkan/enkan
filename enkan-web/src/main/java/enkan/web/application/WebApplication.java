package enkan.web.application;

import enkan.Application;
import enkan.Endpoint;
import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.data.UriAvailable;
import enkan.predicate.PathPredicate;
import enkan.util.MixinUtils;
import enkan.util.Predicates;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An application handles HttpRequest/HttpResponse.
 *
 * @author kawasima
 */
public class WebApplication implements Application<HttpRequest, HttpResponse> {
    private final List<MiddlewareChain<?, ?, ?, ?>> middlewareStack = new LinkedList<>();
    private volatile Supplier<HttpRequest> requestFactory;

    /** Registers a middleware for GET requests matching the given path. */
    public <REQ extends UriAvailable, RES, NREQ, NRES> void get(String path, Middleware<REQ, RES, NREQ, NRES> middleware) {
        use(PathPredicate.GET(path), middleware);
    }

    /** Registers a middleware for POST requests matching the given path. */
    public <REQ extends UriAvailable, RES, NREQ, NRES> void post(String path, Middleware<REQ, RES, NREQ, NRES> middleware) {
        use(PathPredicate.POST(path), middleware);
    }
    /** Registers a middleware for PUT requests matching the given path. */
    public <REQ extends UriAvailable, RES, NREQ, NRES> void put(String path, Middleware<REQ, RES, NREQ, NRES> middleware) {
        use(PathPredicate.PUT(path), middleware);
    }
    /** Registers a middleware for DELETE requests matching the given path. */
    public <REQ extends UriAvailable, RES, NREQ, NRES> void delete(String path, Middleware<REQ, RES, NREQ, NRES> middleware) {
        use(PathPredicate.DELETE(path), middleware);
    }

    public <REQ extends UriAvailable, RES> void use(Predicate<? super REQ> decision, String middlewareName, Endpoint<REQ, RES> endpoint) {
        use(decision, middlewareName, (Middleware<REQ, RES, REQ, RES>) endpoint);
    }

    @Override
    public <REQ, RES, NREQ, NRES> void use(Predicate<? super REQ> decision, String middlewareName, Middleware<REQ, RES, NREQ, NRES> middleware) {
        MiddlewareChain<REQ, RES, NREQ, NRES> chain = new DefaultMiddlewareChain<>(decision, middlewareName, middleware);
        if (!middlewareStack.isEmpty()) {
            middlewareStack.getLast().setNext(cast(chain));
        }
        middlewareStack.addLast(chain);
        requestFactory = null; // invalidate cached factory
    }

    @Override
    public HttpResponse handle(HttpRequest req) {
        return new DefaultMiddlewareChain<> (Predicates.any(), "bootstrap", new Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(HttpRequest req1, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
                return chain.next(req1);
            }
        }).setNext(cast(middlewareStack.getFirst())).next(req);
    }

    /**
     * Creates an HttpRequest that already implements all mixin interfaces
     * declared by the registered middlewares, eliminating per-request proxy
     * creation in the middleware chain.
     *
     * @return a pre-mixed HttpRequest instance
     */
    public HttpRequest createRequest() {
        Supplier<HttpRequest> factory = this.requestFactory;
        if (factory == null) {
            factory = buildRequestFactory();
            this.requestFactory = factory;
        }
        return factory.get();
    }

    private Supplier<HttpRequest> buildRequestFactory() {
        Set<Class<?>> mixinSet = new LinkedHashSet<>();
        for (MiddlewareChain<?, ?, ?, ?> chain : middlewareStack) {
            enkan.annotation.Middleware anno = chain.getMiddleware().getClass()
                    .getAnnotation(enkan.annotation.Middleware.class);
            if (anno != null) {
                for (Class<?> mixin : anno.mixins()) {
                    mixinSet.add(mixin);
                }
            }
        }

        if (mixinSet.isEmpty()) {
            return DefaultHttpRequest::new;
        }

        Class<?>[] mixins = mixinSet.toArray(new Class<?>[0]);
        return MixinUtils.createFactory(new DefaultHttpRequest(), mixins);
    }

    @Override
    public List<MiddlewareChain<?, ?, ?, ?>> getMiddlewareStack() {
        return middlewareStack;
    }

    /**
     * A cast helper of middleware chain
     *
     * @param chain the middleware chain without type parameters
     * @param <REQ> the type of request
     * @param <RES> the type of response
     * @return the middleware chain with type parameters
     */
    @SuppressWarnings("unchecked")
    private <REQ, RES, NREQ, NRES> MiddlewareChain<REQ, RES, NREQ, NRES> cast(MiddlewareChain<?, ?, ?, ?> chain) {
        return (MiddlewareChain<REQ, RES, NREQ, NRES>) chain;
    }
}
