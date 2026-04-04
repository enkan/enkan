package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.ContentNegotiable;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.negotiation.AcceptHeaderNegotiator;
import enkan.web.middleware.negotiation.ContentNegotiator;
import enkan.web.collection.Headers;
import enkan.util.MixinUtils;

import jakarta.ws.rs.core.MediaType;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static enkan.util.ThreadingUtils.*;

/**
 * Accept =&gt; Convert response format.
 *
 * @author kawasima
 */
@Middleware(name = "contentNegotiation", mixins = ContentNegotiable.class)
public class ContentNegotiationMiddleware implements WebMiddleware {
    private ContentNegotiator negotiator;
    private Set<String> allowedTypes;
    private Set<String> allowedLanguages;
    private Set<String> allowedCharsets;
    private Set<String> allowedEncodings;

    public ContentNegotiationMiddleware() {
        negotiator = new AcceptHeaderNegotiator();
        allowedTypes = Set.of("text/html");
        allowedLanguages = Set.of("*");
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        Headers headers = request.getHeaders();
        String accept = headers != null ? Objects.toString(headers.getOrDefault("Accept", "*/*"), "*/*") : "*/*";
        MediaType mediaType = negotiator.bestAllowedContentType(accept, allowedTypes);
        String acceptLanguage = headers != null ? Objects.toString(headers.getOrDefault("Accept-Language", "*"), "*") : "*";
        String lang = negotiator.bestAllowedLanguage(acceptLanguage, allowedLanguages);
        Locale locale = Objects.equals(lang, "*")? null : some(lang, Locale::forLanguageTag).orElse(null);

        request = MixinUtils.mixin(request, ContentNegotiable.class);
        ((ContentNegotiable) request).setMediaType(mediaType);
        ((ContentNegotiable) request).setLocale(locale);

        if (allowedCharsets != null) {
            String acceptCharset = headers != null
                    ? Objects.toString(headers.getOrDefault("Accept-Charset", "*"), "*") : "*";
            String charset = negotiator.bestAllowedCharset(acceptCharset, allowedCharsets);
            ((ContentNegotiable) request).setCharset(charset);
        }
        if (allowedEncodings != null) {
            String acceptEncoding = headers != null
                    ? Objects.toString(headers.getOrDefault("Accept-Encoding", "identity"), "identity") : "identity";
            String encoding = negotiator.bestAllowedEncoding(acceptEncoding, allowedEncodings);
            ((ContentNegotiable) request).setEncoding(encoding);
        }

        return castToHttpResponse(chain.next(request));
    }

    public void setNegotiator(ContentNegotiator negotiator) {
        this.negotiator = negotiator;
    }

    public void setAllowedTypes(Set<String> allowedTypes) {
        this.allowedTypes = Set.copyOf(allowedTypes);
    }

    public void setAllowedLanguages(Set<String> allowedLanguages) {
        this.allowedLanguages = Set.copyOf(allowedLanguages);
    }

    public void setAllowedCharsets(Set<String> allowedCharsets) {
        this.allowedCharsets = allowedCharsets != null ? Set.copyOf(allowedCharsets) : null;
    }

    public void setAllowedEncodings(Set<String> allowedEncodings) {
        this.allowedEncodings = allowedEncodings != null ? Set.copyOf(allowedEncodings) : null;
    }
}
