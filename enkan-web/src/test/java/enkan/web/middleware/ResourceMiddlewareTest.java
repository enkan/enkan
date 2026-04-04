package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.util.Predicates;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;

import static enkan.util.BeanBuilder.*;
import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
class ResourceMiddlewareTest {
    private static final WebMiddleware DUMMY_ENDPOINT = new WebMiddleware() {
        @Override
        public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
            return builder(HttpResponse.of("dummy"))
                    .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                    .build();
        }
    };

    @Test
    void resourceIsFound() throws URISyntaxException {
        ResourceMiddleware middleware = new ResourceMiddleware();

        HttpRequest request = new DefaultHttpRequest();
        request.setRequestMethod("GET");
        request.setUri("/assets/test.txt");
        request.setParams(Parameters.of("A", " B ", "C", 1));
        MiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> chain =
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint", DUMMY_ENDPOINT);
        HttpResponse response = middleware.handle(request, chain);
        assertThat(response.getBody())
                .isEqualTo(new File(ClassLoader.getSystemResource("public/test.txt").toURI()));
    }

    @Test
    void resourceAssetPath() throws URISyntaxException {
        ResourceMiddleware middleware = builder(new ResourceMiddleware())
                .set(ResourceMiddleware::setUriPrefix, (String) null)
                .build();

        HttpRequest request = new DefaultHttpRequest();
        request.setRequestMethod("GET");
        request.setUri("/test.txt");
        request.setParams(Parameters.of("A", " B ", "C", 1));
        MiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> chain =
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint", DUMMY_ENDPOINT);
        HttpResponse response = middleware.handle(request, chain);
        assertThat(response.getBody())
                .isEqualTo(new File(ClassLoader.getSystemResource("public/test.txt").toURI()));
    }

}
