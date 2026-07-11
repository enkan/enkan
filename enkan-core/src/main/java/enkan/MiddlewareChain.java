package enkan;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A chain of middleware.
 *
 * @author kawasima
 */
public interface MiddlewareChain<REQ, RES, NREQ, NRES> {
    /**
     * Set a next middleware.
     *
     * @param next   a next middleware
     * @return this middleware's chain
     */
    MiddlewareChain<REQ, RES, NREQ, NRES> setNext(MiddlewareChain<NREQ, NRES, ?, ?> next);

    /**
     * Get a middleware in this chain.
     *
     * @return middleware
     */
    Middleware<REQ, RES, NREQ, NRES> getMiddleware();

    /**
     * Get a middleware name in this chain.
     *
     * @return the name of middleware
     */
    String getName();

    /**
     * Get a predicate.
     *
     * @return an predicate
     */
    Predicate<? super REQ> getPredicate();

    /**
     * Set a predicate.
     *
     * @param predicate predicate
     */
    void setPredicate(Predicate<? super REQ> predicate);

    /**
     * Process the next middleware.
     *
     * @param req  A request object
     * @return A response object, or {@code null} if a middleware produced none.
     *         An exhausted chain (no middleware matched) throws instead.
     */
    @Nullable RES next(REQ req);
}
