package enkan.middleware;

import enkan.Middleware;
import enkan.MiddlewareChain;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static enkan.util.ReflectionUtils.tryReflection;

/**
 * Load on demand from the name of the middleware class.
 *
 * @param <REQ>  A type of the request
 * @param <RES>  A type of the response
 * @param <NREQ> A type of the next request
 * @param <NRES>A type of the next response
 *
 * @author kawasima
 */
public class LazyLoadMiddleware<REQ, RES, NREQ, NRES> implements Middleware<REQ, RES, NREQ, NRES> {
    private volatile @Nullable Middleware<REQ, RES, NREQ, NRES> instance;
    private final Lock initializingLock = new ReentrantLock();
    private final String middlewareClassName;

    public LazyLoadMiddleware(String middlewareClassName) {
        this.middlewareClassName = middlewareClassName;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <NNREQ, NNRES> RES handle(REQ request, MiddlewareChain<NREQ, NRES, NNREQ, NNRES> chain) {
        Middleware<REQ, RES, NREQ, NRES> loaded = instance;
        if (loaded == null) {
            initializingLock.lock();
            try {
                // Re-check after acquiring the lock (double-checked locking).
                // volatile guarantees the write in another thread is visible here.
                loaded = instance;
                if (loaded == null) {
                    loaded = tryReflection(() -> {
                        Class<Middleware<REQ, RES, NREQ, NRES>> middlewareClass = (Class<Middleware<REQ, RES, NREQ, NRES>>) Class.forName(middlewareClassName);
                        return middlewareClass.getConstructor().newInstance();
                    });
                    instance = loaded;
                }
            } finally {
                initializingLock.unlock();
            }
        }
        return loaded.handle(request, chain);
    }
}
