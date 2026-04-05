package enkan.endpoint.devel;

import enkan.Endpoint;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;

import static enkan.web.util.HttpResponseUtils.contentType;

/**
 * Endpoint that exposes development runtime information as JSON.
 *
 * <p>Serves {@code GET /x-enkan/dev-info} and returns a JSON object containing
 * the WebSocket REPL port, which {@code enkan-repl.js} fetches at startup to
 * discover the ephemeral port assigned by the OS.
 *
 * <p>Register in development mode:
 * <pre>{@code
 * app.use(path("^/x-enkan/dev-info$"),
 *         new LazyLoadMiddleware<>("enkan.endpoint.devel.DevInfoEndpoint"));
 * }</pre>
 *
 * @author kawasima
 */
public class DevInfoEndpoint implements Endpoint<HttpRequest, HttpResponse> {

    /** System property set by {@code WebSocketTransportProvider} after binding. */
    static final String WS_PORT_PROPERTY = "enkan.repl.ws.port";

    @Override
    public HttpResponse handle(HttpRequest request) {
        String wsPort = System.getProperty(WS_PORT_PROPERTY, "-1");
        HttpResponse response = HttpResponse.of("{\"wsPort\":" + wsPort + "}");
        contentType(response, "application/json");
        return response;
    }
}
