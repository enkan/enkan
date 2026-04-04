package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static enkan.web.util.CodecUtils.urlDecode;
import static enkan.web.util.HttpRequestUtils.pathInfo;
import static enkan.web.util.HttpResponseUtils.resourceResponse;

/**
 * @author kawasima
 */
@Middleware(name = "resource")
public class ResourceMiddleware implements WebMiddleware {
    private String rootPath = "public";
    private String uriPrefix = "assets/";

    private static final Set<String> ACCEPTABLE_METHODS = Set.of("GET", "HEAD");

    protected HttpResponse resourceRequest(HttpRequest request, String rootPath) {
        if (ACCEPTABLE_METHODS.contains(
                Objects.toString(request.getRequestMethod(), "").toUpperCase(Locale.ENGLISH))) {
            String path = urlDecode(pathInfo(request)).substring(1);
            if (!path.startsWith(uriPrefix)) {
                return null;
            }
            int len = uriPrefix.length();
            if (len > 0) path = path.substring(len);
            return resourceResponse(path, OptionMap.of("root", rootPath));
        }

        return null;
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        HttpResponse response = resourceRequest(request, rootPath);
        if (response == null) {
            response = castToHttpResponse(chain.next(request));
        }
        return response;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public void setUriPrefix(String uriPrefix) {
        if (uriPrefix == null) {
            this.uriPrefix = "";
        } else {
            if (uriPrefix.startsWith("/")) uriPrefix = uriPrefix.substring(1);
            if (!uriPrefix.endsWith("/")) uriPrefix += "/";
            this.uriPrefix = uriPrefix;
        }
    }
}
