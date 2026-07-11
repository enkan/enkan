package enkan;

import enkan.exception.MisconfigurationException;
import enkan.util.Predicates;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The application applied middlewares.
 * When you use a middleware, call the <code>use</code> method.
 *
 * @author kawasima
 */
public interface Application<AREQ, ARES> {
    /**
     * Declare to use a middleware.
     *
     * @param middleware middleware
     * @param <REQ>  A type of request
     * @param <RES>  A type of response
     * @param <NREQ> A type of the next request
     * @param <NRES> A type of the next response
     */
    default <REQ, RES, NREQ, NRES> void use(Middleware<REQ, RES, NREQ, NRES> middleware) {
        use(Predicates.any(), middleware);
    }

    /**
     * Declare to use a middleware with a predication.
     *
     * @param predicate A predicate for using the middleware
     * @param middleware A middleware
     * @param <REQ>  A type of request
     * @param <RES>  A type of response
     * @param <NREQ> A type of the next request
     * @param <NRES> A type of the next response
     */
    default <REQ, RES, NREQ, NRES> void use(Predicate<? super REQ> predicate, Middleware<REQ, RES, NREQ, NRES> middleware) {
        use(predicate, null, middleware);
    }

    /**
     * Declare to use a middleware with a predication and middleware's name.
     *
     * @param predicate A predicate for using the middleware
     * @param middlewareName the name of a middleware
     * @param middleware A middleware
     * @param <REQ>  A type of request
     * @param <RES>  A type of response
     * @param <NREQ> A type of the next request
     * @param <NRES> A type of the next response
     */
    <REQ, RES, NREQ, NRES> void use(Predicate<? super REQ> predicate, @Nullable String middlewareName, Middleware<REQ, RES, NREQ, NRES> middleware);

    /**
     * Handle a request using middleware stack in this application.
     *
     * @param req   A request object
     * @return      A response object, or {@code null} if the stack yields none
     */
    @Nullable ARES handle(AREQ req);

    /**
     * Validate a middleware stack.
     */
    default void validate() {
        Set<String> priorMiddelwares = new HashSet<>();
        priorMiddelwares.add("");

        for (MiddlewareChain<?,?,?,?> chain : getMiddlewareStack()) {
            Middleware<?,?,?,?> middleware = chain.getMiddleware();
            enkan.annotation.Middleware anno = middleware.getClass().getAnnotation(enkan.annotation.Middleware.class);
            if (anno == null) continue;
            String name = anno.name();
            String[] dependencies = anno.dependencies();
            Set<String> lackMiddlewares = Arrays.stream(dependencies)
                    .filter(d -> !priorMiddelwares.contains(d))
                    .collect(Collectors.toSet());

            if (!lackMiddlewares.isEmpty()) {
                throw new MisconfigurationException("core.MIDDLEWARE_DEPENDENCY", name, lackMiddlewares);
            }
            priorMiddelwares.add(name);
        }
    }

    /**
     * Get all middlewares using by this application.
     *
     * @return List of middlewares
     */
    List<MiddlewareChain<?, ?, ?, ?>> getMiddlewareStack();
}
