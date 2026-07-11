package enkan;

import org.jspecify.annotations.Nullable;

/**
 * Handles a request and calls the next middleware chain and  returns a response.
 *
 * @author kawasima
 */
public interface Middleware<REQ, RES, NREQ, NRES> {
    /**
     * Handles the given request.
     *
     * <p>A middleware may return {@code null} to signal that no response was
     * produced (for example, a decorator that passes through a {@code null}
     * response from downstream). This is distinct from an exhausted chain,
     * which throws rather than returning {@code null}.
     *
     * @param req   A request object
     * @param chain A chain of middlewares
     * @return      A response object, or {@code null} if none was produced
     */
    <NNREQ, NNRES> @Nullable RES handle(REQ req, MiddlewareChain<NREQ, NRES, NNREQ, NNRES> chain);
}
