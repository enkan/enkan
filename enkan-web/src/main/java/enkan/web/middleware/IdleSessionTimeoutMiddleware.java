package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.data.Session;
import enkan.web.util.HttpResponseUtils;

import java.util.Objects;
import java.util.Optional;

import static enkan.util.BeanBuilder.builder;
import static enkan.web.util.HttpResponseUtils.RedirectStatusCode.TEMPORARY_REDIRECT;
import static enkan.util.ThreadingUtils.some;

/**
 * Expires the idle session after a specified number of seconds.
 *
 * @author kawasima
 */
@Middleware(name = "idleSessionTimeout", dependencies = {"session"})
public class IdleSessionTimeoutMiddleware implements WebMiddleware {
    private long timeout = 600;
    private Endpoint<HttpRequest, HttpResponse> timeoutEndpoint = req ->
            HttpResponseUtils.redirect("/", TEMPORARY_REDIRECT);
    private static final String SESSION_KEY = IdleSessionTimeoutMiddleware.class.getName() + "/idleTimeout";

    /**
     * Returns a current time seconds from epoch.
     *
     * @return a current time seconds
     */
    private Long currentTime() {
        return System.currentTimeMillis() / 1000;
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        Optional<Long> endTime = some(request.getSession(),
                session -> session.get(SESSION_KEY),
                obj -> {
                    try {
                        return Long.parseLong(Objects.toString(obj));
                    } catch (NumberFormatException _) {
                        return null;
                    }
                });

        if (endTime.isPresent() && endTime.get() < currentTime()) {
            return builder(timeoutEndpoint.handle(request))
                    .set(HttpResponse::setSession, null)
                    .build();
        } else {
            HttpResponse response = castToHttpResponse(chain.next(request));
            Long nextEndTime = currentTime() + timeout;
            Session session = Optional.ofNullable(response.getSession())
                    .orElse(request.getSession());

            if (session != null) {
                session.put(SESSION_KEY, nextEndTime);
                response.setSession(session);
            }

            return response;
        }
    }

    /**
     * Sets the timeout in seconds.
     * @param timeout the timeout in seconds
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Sets the endpoint to redirect when the session is expired.
     * @param timeoutEndpoint the endpoint to redirect
     */
    public void setIdleTimeoutEndpoint(Endpoint<HttpRequest, HttpResponse> timeoutEndpoint) {
        this.timeoutEndpoint = timeoutEndpoint;
    }
}
