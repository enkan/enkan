package kotowari.example.middleware;

import enkan.MiddlewareChain;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.WebMiddleware;

import java.lang.reflect.Method;

/**
 * Reflection-based adapter for RequestTimeoutMiddleware to avoid compile-time
 * dependency on preview class files.
 */
public class RequestTimeoutDemoMiddleware implements WebMiddleware {
    private final long timeoutMillis;
    private volatile Object delegate;

    public RequestTimeoutDemoMiddleware(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        Object middleware = ensureDelegate();
        try {
            Method handle = middleware.getClass()
                    .getMethod("handle", HttpRequest.class, MiddlewareChain.class);
            return (HttpResponse) handle.invoke(middleware, request, chain);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to delegate to RequestTimeoutMiddleware", e);
        }
    }

    private Object ensureDelegate() {
        if (delegate != null) return delegate;
        synchronized (this) {
            if (delegate != null) return delegate;
            try {
                Class<?> clazz = Class.forName("enkan.web.middleware.RequestTimeoutMiddleware");
                Object instance = clazz.getConstructor().newInstance();
                Method setter = clazz.getMethod("setTimeoutMillis", long.class);
                setter.invoke(instance, timeoutMillis);
                delegate = instance;
                return instance;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to initialize RequestTimeoutMiddleware delegate", e);
            }
        }
    }
}
