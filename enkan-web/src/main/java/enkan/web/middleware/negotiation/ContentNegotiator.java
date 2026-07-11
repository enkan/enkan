package enkan.web.middleware.negotiation;

import org.jspecify.annotations.Nullable;

import jakarta.ws.rs.core.MediaType;
import java.util.Set;

/**
 * @author kawasima
 */
public interface ContentNegotiator {
    @Nullable MediaType bestAllowedContentType(String accept, Set<String> allowedTypes);
    @Nullable String bestAllowedCharset(String acceptsHeader, Set<String> available);
    @Nullable String bestAllowedEncoding(String acceptsHeader, Set<String> available);
    @Nullable String bestAllowedLanguage(String acceptsHeader, Set<String> available);
}
