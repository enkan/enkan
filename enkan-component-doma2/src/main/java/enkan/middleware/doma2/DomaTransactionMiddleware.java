package enkan.middleware.doma2;


import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.component.doma2.DomaProvider;
import enkan.data.Routable;
import enkan.exception.MisconfigurationException;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.ConfigSupport;
import org.seasar.doma.jdbc.tx.EnkanLocalTransactionDataSource;
import org.seasar.doma.jdbc.tx.LocalTransactionManager;
import org.seasar.doma.jdbc.tx.TransactionManager;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;
import java.lang.reflect.Method;

/**
 * Middleware for Doma2 transaction management.
 * This middleware opens and closes a transaction for each request.
 * The transaction type is determined by the {@link Transactional} annotation.
 * This middleware requires {@link DomaProvider} to provide a {@link Config} object.
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "domaTransaction", dependencies = {"routing"})
public class DomaTransactionMiddleware<REQ, RES> implements DecoratorMiddleware<REQ, RES> {
    @Inject
    private DomaProvider domaProvider;

    private @Nullable TransactionManager tm;

    /**
     * Retrieves the transaction type from the given method.
     *
     * @param m the method to inspect
     * @return the transaction type, or null if not found
     */
    private Transactional.@Nullable TxType getTransactionType(Method m) {
        Transactional transactional = m.getDeclaredAnnotation(Transactional.class);
        return transactional != null ? transactional.value() : null;
    }

    @PostConstruct
    private void init() {
        Config defaultConfig = domaProvider.getDefaultConfig();
        if (defaultConfig == null) {
            throw new MisconfigurationException("doma2.TX_MANAGER_NOT_AVAILABLE");
        }
        DataSource ds = defaultConfig.getDataSource(); // returns LocalTransactionDataSource
        if (ds instanceof EnkanLocalTransactionDataSource ltds) {
            tm = new LocalTransactionManager(ltds.getLocalTransaction(ConfigSupport.defaultJdbcLogger));
        } else {
            throw new MisconfigurationException("doma2.TX_MANAGER_NOT_AVAILABLE");
        }
    }

    @Override
    public <NRES, NREQ> @Nullable RES handle(REQ req, MiddlewareChain<REQ, RES, NRES, NREQ> chain) {
        if (req instanceof Routable routable) {
            Method m = routable.getControllerMethod();
            if (m == null) return chain.next(req);
            Transactional.TxType type = getTransactionType(m);

            if (type != null) {
                if (tm == null) {
                    throw new MisconfigurationException("doma2.TX_MANAGER_NOT_AVAILABLE");
                }
                return switch (type) {
                    case REQUIRED -> tm.required(() ->
                            chain.next(req));
                    case REQUIRES_NEW -> tm.requiresNew(() -> chain.next(req));
                    default -> throw new MisconfigurationException("doma2.UNSUPPORTED_TX_TYPE", type);
                };
            }
        }
        return chain.next(req);
    }
}
