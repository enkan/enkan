package kotowari.inject.parameter;

import enkan.web.data.HttpRequest;
import kotowari.inject.ParameterInjector;

public class HttpRequestInjector implements ParameterInjector<HttpRequest> {
    @Override
    public String getName() {
        return "HttpRequest";
    }

    @Override
    public boolean isApplicable(Class<?> type) {
        return HttpRequest.class.isAssignableFrom(type);
    }

    @Override
    public HttpRequest getInjectObject(HttpRequest request) {
        return request;
    }
}
