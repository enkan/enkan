package enkan.web.security.backend;

import enkan.web.data.HttpRequest;
import enkan.security.AuthBackend;
import enkan.util.ThreadingUtils;

import java.security.Principal;
import java.util.Optional;

/**
 * @author kawasima
 */
public class SessionBackend implements AuthBackend<HttpRequest, Principal> {
    @Override
    public Principal parse(HttpRequest request) {
        Optional<Principal> principal = ThreadingUtils.some(request,
                HttpRequest::getSession,
                s -> (Principal) s.get("principal"));
        return principal.orElse(null);
    }

    @Override
    public Principal authenticate(HttpRequest request, Principal principal) {
        return principal;
    }
}
