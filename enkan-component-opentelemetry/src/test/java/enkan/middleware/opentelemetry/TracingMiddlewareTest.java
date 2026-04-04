package enkan.middleware.opentelemetry;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.web.collection.Headers;
import enkan.component.LifecycleManager;
import enkan.component.opentelemetry.OpenTelemetryComponent;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.util.Predicates;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.reflect.Field;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TracingMiddlewareTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private OpenTelemetryComponent otelComponent;
    private TracingMiddleware middleware;

    @BeforeEach
    void setUp() throws Exception {
        otelComponent = new OpenTelemetryComponent(otelTesting.getOpenTelemetry());
        LifecycleManager.start(otelComponent);

        middleware = new TracingMiddleware();
        Field f = TracingMiddleware.class.getDeclaredField("openTelemetry");
        f.setAccessible(true);
        f.set(middleware, otelComponent);
    }

    @AfterEach
    void tearDown() {
        LifecycleManager.stop(otelComponent);
    }

    private HttpRequest buildRequest(String method, String uri) {
        DefaultHttpRequest req = new DefaultHttpRequest();
        req.setRequestMethod(method);
        req.setUri(uri);
        req.setHeaders(Headers.empty());
        req.setServerName("localhost");
        req.setServerPort(8080);
        req.setRemoteAddr("127.0.0.1");
        req.setProtocol("HTTP/1.1");
        return req;
    }

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chainReturning(
            Function<HttpRequest, HttpResponse> fn) {
        Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse> endpoint = new Middleware<>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(
                    HttpRequest req,
                    MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> c) {
                return fn.apply(req);
            }
        };
        var mwChain = new DefaultMiddlewareChain<>(
                Predicates.any(), "tracing", middleware);
        var epChain = new DefaultMiddlewareChain<>(
                Predicates.any(), "endpoint", endpoint);
        mwChain.setNext(epChain);
        return mwChain;
    }

    @Test
    void createsServerSpanWithHttpAttributes() {
        HttpRequest req = buildRequest("GET", "/api/users");
        chainReturning(r -> HttpResponse.of("ok")).next(req);

        var spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);
        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("GET /api/users");
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.request.method")))
                .isEqualTo("GET");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("url.path")))
                .isEqualTo("/api/users");
        assertThat(span.getAttributes().get(AttributeKey.longKey("http.response.status_code")))
                .isEqualTo(200L);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("server.address")))
                .isEqualTo("localhost");
        assertThat(span.getAttributes().get(AttributeKey.longKey("server.port")))
                .isEqualTo(8080L);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("client.address")))
                .isEqualTo("127.0.0.1");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("network.protocol.version")))
                .isEqualTo("1.1");
    }

    @Test
    void setsErrorStatusOn5xx() {
        HttpRequest req = buildRequest("POST", "/fail");
        chainReturning(r -> {
            HttpResponse res = HttpResponse.of("error");
            res.setStatus(503);
            return res;
        }).next(req);

        SpanData span = otelTesting.getSpans().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getAttributes().get(AttributeKey.longKey("http.response.status_code")))
                .isEqualTo(503L);
    }

    @Test
    void doesNotSetErrorStatusOn4xx() {
        HttpRequest req = buildRequest("GET", "/missing");
        chainReturning(r -> {
            HttpResponse res = HttpResponse.of("not found");
            res.setStatus(404);
            return res;
        }).next(req);

        SpanData span = otelTesting.getSpans().get(0);
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    }

    @Test
    void recordsExceptionAndSetsErrorStatus() {
        Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse> throwing = new Middleware<>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(
                    HttpRequest req,
                    MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> c) {
                throw new RuntimeException("boom");
            }
        };
        var mwChain = new DefaultMiddlewareChain<>(
                Predicates.any(), "tracing", middleware);
        var epChain = new DefaultMiddlewareChain<>(
                Predicates.any(), "endpoint", throwing);
        mwChain.setNext(epChain);

        assertThatThrownBy(() -> mwChain.next(buildRequest("GET", "/boom")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        SpanData span = otelTesting.getSpans().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).anyMatch(e -> e.getName().equals("exception"));
        assertThat(span.getAttributes().get(AttributeKey.stringKey("error.type")))
                .isEqualTo("java.lang.RuntimeException");
    }

    @Test
    void spanIsEndedEvenOnException() {
        Middleware<HttpRequest, HttpResponse, HttpRequest, HttpResponse> throwing = new Middleware<>() {
            @Override
            public <NNREQ, NNRES> HttpResponse handle(
                    HttpRequest req,
                    MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> c) {
                throw new RuntimeException("fail");
            }
        };
        var mwChain = new DefaultMiddlewareChain<>(
                Predicates.any(), "tracing", middleware);
        var epChain = new DefaultMiddlewareChain<>(
                Predicates.any(), "endpoint", throwing);
        mwChain.setNext(epChain);

        assertThatThrownBy(() -> mwChain.next(buildRequest("GET", "/")))
                .isInstanceOf(RuntimeException.class);

        assertThat(otelTesting.getSpans()).hasSize(1);
        assertThat(otelTesting.getSpans().get(0).hasEnded()).isTrue();
    }

    @Test
    void extractsW3CTraceContextFromHeaders() {
        HttpRequest req = buildRequest("GET", "/traced");
        req.getHeaders().put("Traceparent",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        chainReturning(r -> HttpResponse.of("ok")).next(req);

        SpanData span = otelTesting.getSpans().get(0);
        assertThat(span.getTraceId())
                .isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(span.getParentSpanId())
                .isEqualTo("b7ad6b7169203331");
    }
}
