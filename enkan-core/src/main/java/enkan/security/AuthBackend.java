package enkan.security;

import org.jspecify.annotations.Nullable;

import java.security.Principal;

/**
 * @author kawasima
 */
public interface AuthBackend<REQ, T> {
    /**
     * Parse the given request for eliciting an authentication data.
     *
     * @param request the given request
     * @return authenticationData, or {@code null} if none could be elicited
     */
    @Nullable T parse(REQ request);

    /**
     * Authenticate the given request.
     *
     * @param request the given request
     * @param authenticationData authentication data
     * @return a principal when authentication is success, or {@code null} otherwise
     */
    @Nullable Principal authenticate(REQ request, T authenticationData);
}
