package enkan.endpoint.devel;

import enkan.web.collection.Headers;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.predicate.PathPredicate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static enkan.util.BeanBuilder.builder;

/**
 * @author kawasima
 */
public class TraceRouting {
    private final String baseUri;
    private final List<Route> routes = new ArrayList<>();

    public static class RouteNotFoundException extends RuntimeException {
        public RouteNotFoundException() {
            super("Requested trace log was not found");
        }
    }

    public TraceRouting(String baseUri) {
        this.baseUri = baseUri;
    }

    public void add(String path, BiConsumer<HttpRequest, OutputStream> action) {
        routes.add(new Route(PathPredicate.GET(baseUri + path), action));
    }

    public HttpResponse handle(HttpRequest request) {
        Optional<Route> found = routes.stream()
                .filter(route -> route.predicate.test(request))
                .findFirst();
        if (found.isPresent()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                found.get().action.accept(request, baos);
                return builder(HttpResponse.of(new ByteArrayInputStream(baos.toByteArray())))
                        .set(HttpResponse::setHeaders,
                                Headers.of("Content-Type", "text/html"))
                        .build();
            } catch (RouteNotFoundException ex) {
                return builder(HttpResponse.of("Not Found"))
                        .set(HttpResponse::setHeaders,
                                Headers.of("Content-Type", "text/html"))
                        .set(HttpResponse::setStatus, 404)
                        .build();
            }
        } else {
            return builder(HttpResponse.of("Not Found"))
                    .set(HttpResponse::setHeaders,
                            Headers.of("Content-Type", "text/html"))
                    .set(HttpResponse::setStatus, 404)
                    .build();
        }
    }

    private record Route(PathPredicate<HttpRequest> predicate, BiConsumer<HttpRequest, OutputStream> action) {
    }
}
