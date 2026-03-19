package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.OptionMap;
import enkan.data.*;
import enkan.middleware.session.MemoryStore;
import enkan.middleware.session.KeyValueStore;
import enkan.exception.MisconfigurationException;
import enkan.util.MixinUtils;

import jakarta.validation.constraints.NotNull;

import static enkan.util.ThreadingUtils.some;

/**
 * The middleware for session management.
 * @author kawasima
 */
@Middleware(name = "session", dependencies = {"cookies"}, mixins = WebSessionAvailable.class)
public class SessionMiddleware implements WebMiddleware {
    @NotNull
    private String cookieName;

    private OptionMap cookieAttrs;

    @NotNull
    private KeyValueStore store;

    public SessionMiddleware() {
        store = new MemoryStore();
        cookieName = "enkan-session";
        cookieAttrs = OptionMap.of("httpOnly", true,
                "path", "/");
    }

    protected void populateAttrs(Cookie cookie) {
        if (cookieAttrs.containsKey("domain")) {
            cookie.setDomain(cookieAttrs.getString("domain"));
        }

        if (cookieAttrs.containsKey("path")) {
            cookie.setPath(cookieAttrs.getString("path"));
        }

        if (cookieAttrs.containsKey("secure")) {
            cookie.setSecure(cookieAttrs.getBoolean("secure"));
        }

        if (cookieAttrs.containsKey("httpOnly")) {
            cookie.setHttpOnly(cookieAttrs.getBoolean("httpOnly"));
        }
    }

    protected void sessionRequest(HttpRequest request) {
        some(request.getCookies(), cs -> cs.get(cookieName))
                .ifPresent(sessionCookie -> {
                    String reqKey = sessionCookie.getValue();
                    Session session = reqKey != null ? (Session) store.read(reqKey) : null;
                    request.setSession(session);
                    if (session != null) {
                        ((WebSessionAvailable) request).setSessionKey(reqKey);
                    }
                });
    }

    protected void sessionResponse(HttpResponse response, HttpRequest request) {
        String sessionKey = null;
        if (request instanceof WebSessionAvailable wsa) {
            sessionKey = wsa.getSessionKey();
        }
        Session session = response.getSession();

        // Invalidate session.
        // - Call response.session == null
        // - response.session.isNew && request.session != null
        if (session == null || (session.isNew() && request.getSession() != null)) {
            store.delete(sessionKey);
        }

        String newSessionKey = null;
        if (session != null) {
            if (!(session instanceof PersistentMarkedSession)) {
                session.persist();
                newSessionKey = store.write(sessionKey, session);
            }
        }
        Cookie cookie = Cookie.create(cookieName, newSessionKey != null ? newSessionKey : sessionKey);
        populateAttrs(cookie);
        if (newSessionKey != null && !newSessionKey.equals(sessionKey)) {
            response.getCookies().put(cookieName, cookie);
        }
    }

    /**
     * Reads the session from the configured store before the downstream chain
     * and persists any session changes back to the store afterward.
     *
     * @param request the incoming HTTP request
     * @param chain   the remaining middleware chain
     * @return the HTTP response, potentially with an updated session cookie
     */
    @Override
public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        request = MixinUtils.mixin(request, WebSessionAvailable.class);
        sessionRequest(request);
        HttpResponse response = castToHttpResponse(chain.next(request));
        sessionResponse(response, request);
        return response;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public void setCookieAttrs(OptionMap cookieAttrs) {
        this.cookieAttrs = cookieAttrs;
    }

    public void setStore(KeyValueStore store) {
        if (store == null) {
            throw new MisconfigurationException("core.NULL_ARGUMENT", "store");
        }
        this.store = store;
    }
}
