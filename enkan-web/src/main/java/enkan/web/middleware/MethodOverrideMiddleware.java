package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * @author kawasima
 */
@Middleware(name = "methodOverride", dependencies = {"params"})
public class MethodOverrideMiddleware implements WebMiddleware {
    private Function<HttpRequest, @Nullable String> getterFunction = createGetter("_method");

    public void setGetterFunction(String functionName) {
        getterFunction = createGetter(functionName);
    }

    public void setGetterFunction(Function<HttpRequest, @Nullable String> getterFunction) {
        this.getterFunction = getterFunction;
    }

    protected Function<HttpRequest, @Nullable String> createQueryGetter(String key) {
        return req -> {
            var params = req.getParams();
            return params != null ? params.get(key) : null;
        };
    }

    /**
     * Create a getter function from headers.
     *
     * @param str header
     * @return A getter function
     */
    protected Function<HttpRequest, @Nullable String> createHeaderGetter(String str) {
        String header = str.toLowerCase();
        return req -> {
            var headers = req.getHeaders();
            return headers != null ? Optional.ofNullable(headers.get(header)).orElse("") : "";
        };
    }

    protected Function<HttpRequest, @Nullable String> createGetter(String str) {
        if (str.substring(0, 2).equalsIgnoreCase("X-")) {
            return createHeaderGetter(str);
        }
        return createQueryGetter(str);
    }

    @Override
    public <NNREQ, NNRES> @Nullable HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        String val = getterFunction.apply(request);
        if (val != null) {
            request.setRequestMethod(val);
        }
        return castToHttpResponse(chain.next(request));
    }
}
