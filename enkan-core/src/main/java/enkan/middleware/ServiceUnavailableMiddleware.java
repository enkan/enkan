package enkan.middleware;

import enkan.Endpoint;
import enkan.DecoratorMiddleware;
import enkan.MiddlewareChain;
import enkan.exception.ServiceUnavailableException;

import org.jspecify.annotations.Nullable;

/**
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "serviceUnavailable")
public class ServiceUnavailableMiddleware<REQ, RES> implements DecoratorMiddleware<REQ, RES> {
    private final Endpoint<REQ, RES> endpoint;

    public ServiceUnavailableMiddleware(Endpoint<REQ, RES> endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public <NNREQ, NNRES> @Nullable RES handle(REQ req, MiddlewareChain<REQ, RES, NNREQ, NNRES> next) {
        if (endpoint != null) {
            return endpoint.handle(req);
        }
        throw new ServiceUnavailableException();
    }
}
