package enkan.middleware.throttling;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;

import enkan.throttling.Throttle;
import enkan.web.middleware.WebMiddleware;
import enkan.web.util.HttpResponseUtils;

import java.util.Collections;
import java.util.List;

import static enkan.util.BeanBuilder.builder;

/**
 * @author kawasima
 */
@Middleware(name = "throttling")
public class ThrottlingMiddleware implements WebMiddleware {
    private List<Throttle> throttles = Collections.emptyList();

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
        if (throttles.stream()
                .anyMatch(throttle -> throttle.apply(request))) {
            return builder(HttpResponseUtils.response("Too Many Request"))
                    .set(HttpResponse::setStatus, 429)
                    .build();
        }
        return castToHttpResponse(chain.next(request));
    }

    public void setThrottles(List<Throttle> throttles) {
        this.throttles = throttles;
    }
}
