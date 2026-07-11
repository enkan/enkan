package enkan.middleware;

import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.data.PrincipalAvailable;
import enkan.security.AuthBackend;
import enkan.util.MixinUtils;
import enkan.util.ThreadingUtils;

import org.jspecify.annotations.Nullable;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The middleware for authentication.
 *
 * @author kawasima
 */
@Middleware(name = "authentication", mixins = PrincipalAvailable.class)
public class AuthenticationMiddleware<REQ, RES, T> implements DecoratorMiddleware<REQ, RES> {
    private static final Logger LOG = Logger.getLogger(AuthenticationMiddleware.class.getName());
    private final List<AuthBackend<REQ, T>> backends;

    public AuthenticationMiddleware(List<AuthBackend<REQ, T>> backends) {
        this.backends = backends;
    }

    @Override
    public <NNREQ, NNRES> @Nullable RES handle(REQ req, MiddlewareChain<REQ, RES, NNREQ, NNRES> next) {
        final REQ request = MixinUtils.mixin(req, PrincipalAvailable.class);
        for (AuthBackend<REQ, T> backend : backends) {
            try {
                Optional<Principal> principal = ThreadingUtils.some(
                        backend.parse(request),
                        data -> backend.authenticate(request, data));
                principal.ifPresent(((PrincipalAvailable) request)::setPrincipal);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Authentication backend " + backend.getClass().getName() + " threw an exception", e);
            }
        }
        return next.next(request);
    }
}
