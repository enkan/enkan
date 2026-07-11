package kotowari.middleware;

import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.component.TransactionComponent;
import enkan.data.Routable;
import enkan.exception.MisconfigurationException;
import enkan.exception.UnreachableException;

import org.jspecify.annotations.Nullable;

import jakarta.inject.Inject;
import jakarta.transaction.*;
import java.lang.reflect.Method;

/**
 * @author kawasima
 */
public class TransactionMiddleware<REQ, RES> implements DecoratorMiddleware<REQ, RES> {
    @Inject
    private TransactionComponent transactionComponent;

    private Transactional.@Nullable TxType getTransactionType(Method m) {
        Transactional transactional = m.getDeclaredAnnotation(Transactional.class);
        return transactional != null ? transactional.value() : null;
    }

    @Override
    public <NNREQ, NNRES> @Nullable RES handle(REQ req, MiddlewareChain<REQ, RES, NNREQ, NNRES> chain) {
        RES res;
        if (req instanceof Routable routable) {
            Method m = routable.getControllerMethod();
            Transactional.TxType type= getTransactionType(m);
            if (type != null) {
                if (transactionComponent == null) {
                    throw new MisconfigurationException("kotowari.MISSING_INJECTION",
                            "transactionComponent", "TransactionMiddleware");
                }
                TransactionManager tm = transactionComponent.getTransactionManager();
                switch(type) {
                    case REQUIRED:
                        try {
                            tm.begin();
                            res = chain.next(req);
                            tm.commit();
                        } catch (NotSupportedException e) {
                            throw new UnreachableException(e);
                        } catch (SystemException e) {
                            throw new MisconfigurationException("kotowari.TX_UNEXPECTED_CONDITION", e.errorCode, e);
                        } catch (HeuristicMixedException e) {
                            throw new MisconfigurationException("kotowari.TX_HEURISTIC_MIXED", e.getMessage(), e);
                        } catch (HeuristicRollbackException e) {
                            throw new MisconfigurationException("kotowari.TX_HEURISTIC_ROLLBACK", e.getMessage(), e);
                        } catch (RollbackException e) {
                            throw new MisconfigurationException("kotowari.TX_ROLLBACK", e.getMessage(), e);
                        } catch (RuntimeException e) {
                            try {
                                tm.rollback();
                            } catch (SystemException se) {
                                throw new MisconfigurationException("kotowari.TX_UNEXPECTED_CONDITION", se.errorCode, se);
                            }
                            throw e;
                        }
                        break;
                    default:
                        throw new MisconfigurationException("kotowari.UNSUPPORTED_TX_TYPE", type);
                }
            } else {
                res = chain.next(req);
            }
        } else {
            res = chain.next(req);
        }
        return res;
    }
}
