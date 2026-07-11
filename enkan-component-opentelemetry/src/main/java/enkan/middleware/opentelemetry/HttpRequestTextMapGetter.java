package enkan.middleware.opentelemetry;

import enkan.web.data.HttpRequest;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.jspecify.annotations.Nullable;

import java.util.Collections;

/**
 * Bridges enkan's {@link HttpRequest} headers to OpenTelemetry's
 * {@link TextMapGetter} interface for W3C Trace Context extraction.
 *
 * @author kawasima
 */
enum HttpRequestTextMapGetter implements TextMapGetter<HttpRequest> {
    INSTANCE;

    @Override
    public Iterable<String> keys(HttpRequest request) {
        if (request == null || request.getHeaders() == null) {
            return Collections.emptyList();
        }
        return request.getHeaders().keySet();
    }

    @Override
    public @Nullable String get(@Nullable HttpRequest request, @Nullable String key) {
        if (request == null || request.getHeaders() == null || key == null) {
            return null;
        }
        return request.getHeaders().get(key);
    }
}
