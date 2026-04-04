package enkan.web.endpoint;

import enkan.Endpoint;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.HttpResponseUtils;

/**
 * @author kawasima
 */
public class ResourceEndpoint implements Endpoint<HttpRequest, HttpResponse> {
    private final String path;

    public ResourceEndpoint(String path) {
        this.path = path;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return HttpResponseUtils.resourceResponse(path, OptionMap.empty());
    }
}
