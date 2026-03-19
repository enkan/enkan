package kotowari.middleware;

import enkan.Endpoint;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.collection.Parameters;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.data.Routable;
import enkan.util.Predicates;
import kotowari.routing.Routes;
import kotowari.routing.controller.ExampleController;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RoutingMiddleware.
 */
class RoutingMiddlewareTest {

    // A second controller for distinguishing routes
    public static class OtherController {
        public String show() {
            return "other-show";
        }
    }

    @Test
    void matchedRouteSetsControllerAndActionOnRequest() {
        Routes routes = Routes.define(r ->
                r.get("/test").to(ExampleController.class, "method1")
        ).compile();
        RoutingMiddleware middleware = new RoutingMiddleware(routes);

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/test")
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setHeaders, Headers.empty())
                .set(HttpRequest::setParams, Parameters.empty())
                .build();

        HttpResponse[] captured = new HttpResponse[1];
        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint",
                        (Endpoint<HttpRequest, HttpResponse>) req -> {
                            // Verify the request has been enriched with routing info
                            assertThat(req).isInstanceOf(Routable.class);
                            assertThat(((Routable) req).getControllerClass()).isEqualTo(ExampleController.class);
                            assertThat(((Routable) req).getControllerMethod()).isNotNull();
                            assertThat(((Routable) req).getControllerMethod().getName()).isEqualTo("method1");
                            return HttpResponse.of("OK");
                        }));

        assertThat(response).isNotNull();
        assertThat(response.getBodyAsString()).isEqualTo("OK");
    }

    @Test
    void unmatchedRouteReturns404() {
        Routes routes = Routes.define(r ->
                r.get("/exists").to(ExampleController.class, "method1")
        ).compile();
        RoutingMiddleware middleware = new RoutingMiddleware(routes);

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/not-found")
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setHeaders, Headers.empty())
                .set(HttpRequest::setParams, Parameters.empty())
                .build();

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint",
                        (Endpoint<HttpRequest, HttpResponse>) req -> HttpResponse.of("should not reach")));

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void routeParametersAreMergedIntoRequestParams() {
        Routes routes = Routes.define(r ->
                r.get("/users/:id").to(ExampleController.class, "method1")
        ).compile();
        RoutingMiddleware middleware = new RoutingMiddleware(routes);

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/users/55")
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setHeaders, Headers.empty())
                .set(HttpRequest::setParams, Parameters.empty())
                .build();

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint",
                        (Endpoint<HttpRequest, HttpResponse>) req -> {
                            assertThat(req.getParams().get("id")).isEqualTo("55");
                            return HttpResponse.of("OK");
                        }));

        assertThat(response).isNotNull();
        assertThat(response.getBodyAsString()).isEqualTo("OK");
    }

    @Test
    void controllerClassIsResolvedFromRoute() {
        Routes routes = Routes.define(r -> {
            r.get("/example").to(ExampleController.class, "method1");
            r.get("/other").to(OtherController.class, "show");
        }).compile();
        RoutingMiddleware middleware = new RoutingMiddleware(routes);

        // Request hitting the second route
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setUri, "/other")
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setHeaders, Headers.empty())
                .set(HttpRequest::setParams, Parameters.empty())
                .build();

        HttpResponse response = middleware.handle(request,
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint",
                        (Endpoint<HttpRequest, HttpResponse>) req -> {
                            assertThat(((Routable) req).getControllerClass()).isEqualTo(OtherController.class);
                            assertThat(((Routable) req).getControllerMethod().getName()).isEqualTo("show");
                            return HttpResponse.of("OK");
                        }));

        assertThat(response).isNotNull();
    }
}
