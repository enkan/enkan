package enkan.middleware.jpa;

import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.data.Routable;
import enkan.data.jpa.EntityManageable;
import enkan.exception.MisconfigurationException;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;
import java.lang.reflect.Method;
import java.util.Optional;

@Middleware(name = "nonJtaTransaction", dependencies = {"entityManager", "routing"})
public class NonJtaTransactionMiddleware<REQ, RES> implements DecoratorMiddleware<REQ, RES> {
    private Transactional.@Nullable TxType getTransactionType(Class<?> cls) {
        Transactional transactional = cls.getDeclaredAnnotation(Transactional.class);
        return transactional != null ? transactional.value() : null;
    }

    private Transactional.@Nullable TxType getTransactionType(Method m) {
        Transactional transactional = m.getDeclaredAnnotation(Transactional.class);
        return transactional != null ? transactional.value() : null;
    }

    @Override
    public <NNREQ, NNRES> @Nullable RES handle(REQ req, MiddlewareChain<REQ, RES, NNREQ, NNRES> chain) {
        EntityManager em = Optional.ofNullable(req)
                .filter(EntityManageable.class::isInstance)
                .map(EntityManageable.class::cast)
                .map(EntityManageable::getEntityManager)
                .orElseThrow(() -> new MisconfigurationException("jpa.NOT_ENTITY_MANAGEABLE_REQUEST"));
        if (req instanceof Routable routable) {
            Transactional.TxType type = getTransactionType(routable.getControllerClass());
            Method m = routable.getControllerMethod();
            if (m != null) {
                type = Optional.ofNullable(getTransactionType(m)).orElse(type);
            }
            if (type != null) {
                return switch (type) {
                    case REQUIRED, REQUIRES_NEW -> {
                        em.getTransaction().begin();
                        try {
                            @Nullable RES ret = chain.next(req);
                            if (em.getTransaction().getRollbackOnly()) {
                                em.getTransaction().rollback();
                            } else {
                                em.getTransaction().commit();
                            }
                            yield ret;
                        } catch (Throwable t) {
                            if (em.getTransaction().isActive()) {
                                em.getTransaction().rollback();
                            }
                            throw t;
                        }
                    }
                    default -> throw new MisconfigurationException("jpa.UNSUPPORTED_TX_TYPE", type);
                };
            }
        }
        return chain.next(req);
    }
}
