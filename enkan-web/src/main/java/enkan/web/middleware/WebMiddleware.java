package enkan.web.middleware;

import enkan.DecoratorMiddleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;

/**
 * A {@link DecoratorMiddleware} specialised for {@code HttpRequest}/{@code HttpResponse}.
 *
 * <p>This interface is the standard base for web-layer middleware that does not
 * perform request/response type conversion.
 *
 * <p>A convenience {@link #castToHttpResponse(Object)} default method is provided
 * for implementations that need to coerce the value returned by
 * {@code chain.next(req)}.
 *
 * @author kawasima
 */
public interface WebMiddleware extends DecoratorMiddleware<HttpRequest, HttpResponse> {

    /**
     * Casts an arbitrary response object to {@link HttpResponse}.
     *
     * @param response the response object returned by the next middleware
     * @return the same object cast to {@link HttpResponse}, or {@code null} if
     *         {@code response} is {@code null}
     * @throws MisconfigurationException if {@code response} is non-null but not
     *         an instance of {@link HttpResponse}
     */
    default HttpResponse castToHttpResponse(Object response) {
        if (response == null) {
            return null;
        } else if (response instanceof HttpResponse httpResponse) {
            return httpResponse;
        } else {
            throw new MisconfigurationException("web.RESPONSE_TYPE_MISMATCH");
        }
    }
}
