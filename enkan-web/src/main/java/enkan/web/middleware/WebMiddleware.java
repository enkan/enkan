package enkan.web.middleware;

import enkan.DecoratorMiddleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;

import org.jspecify.annotations.Nullable;

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
     * Casts the response object returned by {@code chain.next(req)} to
     * {@link HttpResponse}.
     *
     * <p>A middleware may yield a {@code null} response, which is passed through
     * unchanged. (An exhausted chain throws rather than returning {@code null}.)
     *
     * @param response the response object returned by the next middleware
     * @return the same object cast to {@link HttpResponse}, or {@code null} if
     *         {@code response} is {@code null}
     * @throws MisconfigurationException if {@code response} is non-null but not
     *         an instance of {@link HttpResponse}
     */
    default @Nullable HttpResponse castToHttpResponse(@Nullable Object response) {
        if (response == null) {
            return null;
        } else if (response instanceof HttpResponse httpResponse) {
            return httpResponse;
        } else {
            throw new MisconfigurationException("web.RESPONSE_TYPE_MISMATCH");
        }
    }
}
