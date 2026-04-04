package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.HttpResponseUtils;
import enkan.util.MimeTypeUtils;

/**
 * Adds default Content-Type header if not.
 *
 * @author kawasima
 */
@Middleware(name = "contentType")
public class ContentTypeMiddleware implements WebMiddleware {
    protected void contentTypeResponse(HttpResponse response, HttpRequest request) {
        if (HttpResponseUtils.getHeader(response, "Content-Type") == null) {
            String uri = request.getUri();

            String type = MimeTypeUtils.extMimeType(uri);
            if (type == null) {
                type = response.getBody() instanceof String ? "text/plain" : "application/octet-stream";
            }
            HttpResponseUtils.contentType(response, type);
        }
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        HttpResponse response = castToHttpResponse(chain.next(request));
        if (response != null) {
            contentTypeResponse(response, request);
        }
        return response;
    }
}
