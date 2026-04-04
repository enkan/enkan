package enkan.web.middleware;

import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.normalizer.TrimNormalizer;
import enkan.util.Predicates;
import org.junit.jupiter.api.Test;

import static enkan.web.middleware.NormalizationMiddleware.normalization;
import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * @author kawasima
 */
class NormalizationMiddlewareTest {
    private static final WebMiddleware DUMMY_ENDPOINT = new WebMiddleware() {
        @Override
        public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {
            return builder(HttpResponse.of("dummy"))
                    .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/plain"))
                    .build();
        }
    };

    @Test
    void noNormalization() {
        NormalizationMiddleware middleware = new NormalizationMiddleware();
        HttpRequest request = new DefaultHttpRequest();
        request.setParams(Parameters.of("A", "B", "C", 1));
        MiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> chain =
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint", DUMMY_ENDPOINT);
        middleware.handle(request, chain);
        assertThat(request.getParams())
                .containsExactly(entry("A", "B"),
                        entry("C", 1));
    }

    @Test
    void trimNormalization() {
        NormalizationMiddleware middleware = new NormalizationMiddleware(
                normalization(Predicates.any(), new TrimNormalizer())
        );
        HttpRequest request = new DefaultHttpRequest();
        request.setParams(Parameters.of("A", " B ", "C", 1));
        MiddlewareChain<HttpRequest, HttpResponse, HttpRequest, HttpResponse> chain =
                new DefaultMiddlewareChain<>(Predicates.any(), "endpoint", DUMMY_ENDPOINT);
        middleware.handle(request, chain);
        assertThat(request.getParams())
                .containsExactly(entry("A", "B"),
                        entry("C", 1));
    }

}
