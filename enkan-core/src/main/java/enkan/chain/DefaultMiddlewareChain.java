package enkan.chain;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.data.Traceable;
import enkan.exception.MisconfigurationException;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The default chain of middleware.
 *
 * @author kawasima
 */
public class DefaultMiddlewareChain<REQ, RES, NREQ, NRES> implements MiddlewareChain<REQ, RES, NREQ, NRES> {
    /**
     * Sentinel installed as the {@code chain} of the last node in a stack.
     * Reaching it means a request was dispatched past every middleware without
     * any of them producing a response — always a chain-wiring mistake, so it
     * fails loudly instead of returning {@code null}.
     */
    @SuppressWarnings("rawtypes")
    private static final MiddlewareChain TERMINAL = new MiddlewareChain() {
        @Override public MiddlewareChain setNext(MiddlewareChain next) {
            throw new UnsupportedOperationException("terminal chain");
        }
        @Override public Middleware getMiddleware() {
            throw new UnsupportedOperationException("terminal chain");
        }
        @Override public String getName() {
            return "terminal";
        }
        @Override public Predicate getPredicate() {
            throw new UnsupportedOperationException("terminal chain");
        }
        @Override public void setPredicate(Predicate predicate) {
            throw new UnsupportedOperationException("terminal chain");
        }
        @Override public Object next(Object req) {
            throw new MisconfigurationException("core.MIDDLEWARE_CHAIN_EXHAUSTED");
        }
    };

    @SuppressWarnings("unchecked")
    private static <A, B> MiddlewareChain<A, B, ?, ?> terminal() {
        return (MiddlewareChain<A, B, ?, ?>) TERMINAL;
    }

    private Predicate<? super REQ> predicate;
    private final Middleware<REQ, RES, NREQ, NRES> middleware;
    private final String middlewareName;
    private MiddlewareChain<NREQ, NRES, ?, ?> chain = terminal();


    /**
     * Creates the chain of middleware.
     * If middlewareName is not given, the name of middleware is given the default from the annotation.
     *
     * @param predicate       a condition of applying the middleware
     * @param middlewareName  a name of middleware
     * @param middleware      a middleware
     */
    public DefaultMiddlewareChain(Predicate<? super REQ> predicate, @Nullable String middlewareName, Middleware<REQ, RES, NREQ, NRES> middleware) {
        this.predicate = predicate;
        this.middleware = middleware;
        enkan.annotation.Middleware anno = middleware.getClass().getAnnotation(enkan.annotation.Middleware.class);
        if (middlewareName != null) {
            this.middlewareName = middlewareName;
        } else if (anno != null) {
            this.middlewareName = anno.name();
        } else {
            this.middlewareName = "Anonymous(" + middleware + ")";
        }
    }

    /**
     * Sets the chain of middleware.
     *
     * @param next {@inheritDoc}
     * @return     {@inheritDoc}
     */
    @Override
    public MiddlewareChain<REQ, RES, NREQ, NRES> setNext(MiddlewareChain<NREQ, NRES, ?, ?> next) {
        this.chain = next;
        return this;
    }

    /**
     * Gets middleware.
     *
     * @return {@inheritDoc}
     */
    @Override
    public Middleware<REQ, RES, NREQ, NRES> getMiddleware() {
        return middleware;
    }

    protected void writeTraceLog(@Nullable Object reqOrRes, String middlewareName) {
        if (reqOrRes instanceof Traceable t) {
            t.getTraceLog().write(middlewareName);
        }
    }

    /**
     * Dispatches a request to the chain of middleware.
     *
     * @param req  {@inheritDoc}
     * @return     {@inheritDoc}
     */
    // The unchecked casts below are safe when the predicate is false: in that
    // case the middleware is skipped entirely and the request is passed through
    // unchanged, so REQ == NREQ and RES == NRES at the call site.
    @SuppressWarnings("unchecked")
    @Override
    public @Nullable RES next(REQ req) {
        writeTraceLog(req, middlewareName);

        if (predicate.test(req)) {
            RES res = middleware.handle(req, chain);
            writeTraceLog(res, middlewareName);
            return res;
        } else {
            // chain is never null: the last node keeps the TERMINAL sentinel,
            // whose next() throws rather than yielding a null response.
            NRES res = chain.next((NREQ) req);
            writeTraceLog(res, middlewareName);
            return (RES) res;
        }
    }

    /**
     * Gets the name of middleware.
     *
     * @return {@inheritDoc}
     */
    @Override
    public String getName() {
        return middlewareName;
    }

    /**
     * Gets the predicate.
     *
     * @return {@inheritDoc}
     */
    @Override
    public Predicate<? super REQ> getPredicate() {
        return predicate;
    }

    /**
     * Sets the predicate.
     *
     * <p>
     * If this predicate returns true, middleware will be applied.
     * </p>
     *
     * @param predicate predicate for applying the middleware.
     */
    @Override
    public void setPredicate(Predicate<? super REQ> predicate) {
        this.predicate = predicate;
    }

    @Override
    public String toString() {
        return predicate.toString() + "   " + middlewareName
                + " (" + middleware + ")";
    }
}
