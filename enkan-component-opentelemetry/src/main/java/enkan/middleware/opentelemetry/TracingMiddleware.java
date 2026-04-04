package enkan.middleware.opentelemetry;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.component.opentelemetry.OpenTelemetryComponent;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.WebMiddleware;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

import jakarta.inject.Inject;

/**
 * Middleware that creates OpenTelemetry spans for each HTTP request.
 *
 * <p>Extracts W3C Trace Context ({@code traceparent}/{@code tracestate}) from
 * incoming request headers, creates a SERVER span with HTTP semantic convention
 * attributes, and ensures the span is ended even if an exception occurs.</p>
 *
 * @author kawasima
 */
@Middleware(name = "tracing")
public class TracingMiddleware implements WebMiddleware {
    @Inject
    private OpenTelemetryComponent openTelemetry;

    private static final AttributeKey<String> HTTP_REQUEST_METHOD =
            AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> URL_PATH =
            AttributeKey.stringKey("url.path");
    private static final AttributeKey<String> URL_QUERY =
            AttributeKey.stringKey("url.query");
    private static final AttributeKey<String> SERVER_ADDRESS =
            AttributeKey.stringKey("server.address");
    private static final AttributeKey<Long> SERVER_PORT =
            AttributeKey.longKey("server.port");
    private static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE =
            AttributeKey.longKey("http.response.status_code");
    private static final AttributeKey<String> NETWORK_PROTOCOL_VERSION =
            AttributeKey.stringKey("network.protocol.version");
    private static final AttributeKey<String> CLIENT_ADDRESS =
            AttributeKey.stringKey("client.address");
    private static final AttributeKey<String> ERROR_TYPE =
            AttributeKey.stringKey("error.type");

    @Override
    public <NNREQ, NNRES> HttpResponse handle(
            HttpRequest request,
            MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> chain) {

        Tracer tracer = openTelemetry.getTracer();

        // Extract propagated context from incoming headers
        TextMapPropagator propagator = openTelemetry.getOpenTelemetry()
                .getPropagators()
                .getTextMapPropagator();
        Context extractedContext = propagator.extract(
                Context.current(), request, HttpRequestTextMapGetter.INSTANCE);

        String method = request.getRequestMethod() != null
                ? request.getRequestMethod() : "UNKNOWN";
        String spanName = method + " " + request.getUri();

        Span span = tracer.spanBuilder(spanName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(HTTP_REQUEST_METHOD, method)
                .setAttribute(URL_PATH, request.getUri())
                .startSpan();

        if (request.getQueryString() != null) {
            span.setAttribute(URL_QUERY, request.getQueryString());
        }
        if (request.getServerName() != null) {
            span.setAttribute(SERVER_ADDRESS, request.getServerName());
        }
        if (request.getServerPort() > 0) {
            span.setAttribute(SERVER_PORT, (long) request.getServerPort());
        }
        if (request.getRemoteAddr() != null) {
            span.setAttribute(CLIENT_ADDRESS, request.getRemoteAddr());
        }
        if (request.getProtocol() != null) {
            String protocol = request.getProtocol();
            if (protocol.startsWith("HTTP/")) {
                span.setAttribute(NETWORK_PROTOCOL_VERSION, protocol.substring(5));
            }
        }

        try (Scope ignored = span.makeCurrent()) {
            HttpResponse response = castToHttpResponse(chain.next(request));

            if (response != null) {
                int statusCode = response.getStatus();
                span.setAttribute(HTTP_RESPONSE_STATUS_CODE, (long) statusCode);
                if (statusCode >= 500) {
                    span.setStatus(StatusCode.ERROR);
                }
            }

            return response;
        } catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.recordException(t);
            span.setAttribute(ERROR_TYPE, t.getClass().getName());
            throw t;
        } finally {
            span.end();
        }
    }
}
