package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.HttpResponseUtils;

import static enkan.web.util.HttpResponseUtils.getHeader;

/**
 * Adds default charset to Content-Type header if not.
 *
 * @author kawasima
 */
@Middleware(name = "defaultCharset")
public class DefaultCharsetMiddleware implements WebMiddleware {
    private String defaultCharset = "UTF-8";

    protected boolean isTextBasedContentType(String contentType) {
        if (contentType == null) return false;
        // Media type comparisons are case-insensitive per RFC 7231 §3.1.1.1.
        // Use regionMatches(true, ...) to avoid allocating a lowercased String on each call.
        int len = contentType.length();
        return (len >= 5  && contentType.regionMatches(true, 0, "text/", 0, 5))
                || (len >= 15 && contentType.regionMatches(true, 0, "application/xml", 0, 15))
                || (len >= 16 && contentType.regionMatches(true, 0, "application/json", 0, 16))
                || (len >= 19 && contentType.regionMatches(true, 0, "application/ld+json", 0, 19))
                || (len >= 21 && contentType.regionMatches(true, 0, "application/xhtml+xml", 0, 21));
    }

    protected boolean isContainsCharset(String contentType) {
        if (contentType == null) return false;
        int len = contentType.length();
        for (int i = 0, end = len - 8; i <= end; i++) {
            if (contentType.regionMatches(true, i, "charset=", 0, 8)) {
                return true;
            }
        }
        return false;
    }

    protected void addCharset(HttpResponse response, String charset) {
        String contentType = getHeader(response, "Content-Type");
        if (isTextBasedContentType(contentType)
                && !isContainsCharset(contentType)) {
            HttpResponseUtils.charset(response, charset);
        }
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        HttpResponse response = castToHttpResponse(chain.next(request));
        if (response != null) {
            addCharset(response, defaultCharset);
        }
        return response;
    }

    public void setDefaultCharset(String defaultCharset) {
        this.defaultCharset = defaultCharset;
    }
}
