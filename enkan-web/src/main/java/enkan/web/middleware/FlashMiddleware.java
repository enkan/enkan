package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.data.Flash;
import enkan.data.FlashAvailable;
import enkan.data.Session;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import org.jspecify.annotations.Nullable;
import enkan.web.data.PersistentMarkedSession;
import enkan.util.MixinUtils;

/**
 * Adds session-based flash store.
 *
 * @author kawasima
 */
@Middleware(name = "flash", dependencies = {"session"}, mixins = FlashAvailable.class)
public class FlashMiddleware implements WebMiddleware {
    private String flashKey = "_flash";

    /**
     * Make the request to handle a flash.
     *
     * @param request request
     */
    protected void flashRequest(HttpRequest request) {
        Session session = request.getSession();
        if (session != null && session.containsKey(flashKey)) {
            Flash<?> flash = (Flash<?>) session.remove(flashKey);
            if (flash != null) {
                request.setFlash(flash);
            }
        }
    }

    /**
     * Make the response to handle a flash.
     *
     * @param response response
     * @param request  request
     */
    protected void flashResponse(HttpResponse response, HttpRequest request) {
        if (response == null) return;

        Session session = response.getSession();
        if (session == null || session instanceof PersistentMarkedSession) {
            session = request.getSession();
        }

        Flash<?> responseFlash = response.getFlash();
        if (responseFlash != null) {
            if (session == null) {
                session = new Session();
            }
            session.put(flashKey, responseFlash);
        }

        if (session != null) {
            response.setSession(session);
        }
    }

    @Override
    public <NNREQ, NNRES> @Nullable HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> next) {
        request = MixinUtils.mixin(request, FlashAvailable.class);
        flashRequest(request);

        HttpResponse response = castToHttpResponse(next.next(request));

        if (response != null) {
            flashResponse(response, request);
        }

        return response;
    }

    /**
     * Sets the key of flash in a session.
     *
     * @param flashKey the key of flash in a session
     */
    public void setFlashKey(String flashKey) {
        this.flashKey = flashKey;
    }
}
