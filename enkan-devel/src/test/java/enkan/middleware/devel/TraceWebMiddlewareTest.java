package enkan.middleware.devel;

import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TraceWebMiddlewareTest {

    @Test
    void updatedMountPathIsApplied() {
        TraceWebMiddleware middleware = new TraceWebMiddleware();
        middleware.setMountPath("/debug/requests");
        try {
            DefaultHttpRequest request = new DefaultHttpRequest();
            request.setUri("/debug/requests/");
            request.setRequestMethod("GET");
            request.setHeaders(Headers.empty());

            AtomicBoolean called = new AtomicBoolean(false);
            HttpResponse response = middleware.handle(request, new TestMiddlewareChain(req -> {
                called.set(true);
                return HttpResponse.of("unreachable");
            }));

            assertThat(called).isFalse();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeaders().get("Content-Type")).isEqualTo("text/html");
        } finally {
            middleware.close();
        }
    }

    @Test
    void missingTraceIdReturns404() {
        TraceWebMiddleware middleware = new TraceWebMiddleware();
        try {
            DefaultHttpRequest request = new DefaultHttpRequest();
            request.setUri("/x-enkan/requests/not-found-id");
            request.setRequestMethod("GET");
            request.setHeaders(Headers.empty());

            AtomicBoolean called = new AtomicBoolean(false);
            HttpResponse response = middleware.handle(request, new TestMiddlewareChain(req -> {
                called.set(true);
                return HttpResponse.of("unreachable");
            }));

            assertThat(called).isFalse();
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getBodyAsString()).contains("Not Found");
        } finally {
            middleware.close();
        }
    }
}
