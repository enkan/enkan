package enkan.web.security.backend;

import enkan.web.data.HttpRequest;
import enkan.security.AuthBackend;
import enkan.web.collection.Headers;

import org.jspecify.annotations.Nullable;

import java.security.Principal;

/**
 * @author kawasima
 */
public class TokenBackend implements AuthBackend<HttpRequest, String> {
    private String tokenName = "Token";

    protected @Nullable String parseAuthorizationHeader(HttpRequest request, String tokenName) {
        Headers headers = request.getHeaders();
        Object authHeader = headers != null ? headers.get("Authorization") : null;
        if (authHeader == null) return null;
        String auth = authHeader.toString();
        String prefix = tokenName + " ";
        if (auth.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return auth.substring(prefix.length()).trim();
        }
        return null;
    }

    @Override
    public @Nullable String parse(HttpRequest request) {
        return parseAuthorizationHeader(request, tokenName);
    }

    @Override
    public @Nullable Principal authenticate(HttpRequest request, String token) {
        return null;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }
}
