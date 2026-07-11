package enkan.middleware.jooq;

import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.component.jooq.JooqProvider;
import enkan.data.Extendable;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

/**
 * Provides a non-transactional jOOQ {@link DSLContext} to downstream handlers.
 *
 * <p>On every request, stores the {@link DSLContext} in the request's
 * {@link Extendable} extensions under the key {@code "jooqDslContext"}.
 * This allows controllers and downstream middlewares to retrieve it via
 * {@code request.getExtension("jooqDslContext")}.
 *
 * <p>For transactional support, stack {@link JooqTransactionMiddleware}
 * after this middleware; it will replace the DSLContext with a
 * transaction-scoped one when {@code @Transactional} is present.
 *
 * <p>Without {@link JooqTransactionMiddleware}, queries run in auto-commit mode.
 */
@Middleware(name = "jooqDslContext")
public class JooqDslContextMiddleware<REQ, RES> implements DecoratorMiddleware<REQ, RES> {
    @Inject
    private JooqProvider jooqProvider;

    private @Nullable DSLContext dsl;

    @PostConstruct
    void init() {
        dsl = jooqProvider.getDSLContext();
    }

    @Override
    public <NNREQ, NNRES> @Nullable RES handle(REQ req, MiddlewareChain<REQ, RES, NNREQ, NNRES> chain) {
        if (req instanceof Extendable e) {
            e.setExtension("jooqDslContext", dsl);
        }
        return chain.next(req);
    }
}
