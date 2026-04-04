package enkan.endpoint.devel;

import enkan.Endpoint;
import enkan.collection.OptionMap;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.HttpResponseUtils;

import java.util.Map;

/**
 * Endpoint that serves the REPL console assets (xterm.js terminal UI).
 *
 * <p>Register with a path predicate for {@code /x-enkan/repl/}:
 * <pre>{@code
 * app.use(PathPredicate.ANY("^/x-enkan/repl/.*"),
 *         new LazyLoadMiddleware<>("enkan.endpoint.devel.ReplConsoleEndpoint"));
 * }</pre>
 *
 * @author kawasima
 */
public class ReplConsoleEndpoint implements Endpoint<HttpRequest, HttpResponse> {
    private static final String MOUNT_PATH = "/x-enkan/repl/";
    private static final String RESOURCE_ROOT = "enkan/repl";

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=UTF-8",
            ".js", "application/javascript; charset=UTF-8",
            ".css", "text/css; charset=UTF-8"
    );

    @Override
    public HttpResponse handle(HttpRequest request) {
        String uri = request.getUri();
        if (!uri.startsWith(MOUNT_PATH)) {
            return null;
        }

        String filename = uri.substring(MOUNT_PATH.length());
        if (filename.isEmpty()) {
            filename = "index.html";
        }

        // Prevent directory traversal
        if (filename.contains("..") || filename.contains("/")) {
            return null;
        }

        HttpResponse response = HttpResponseUtils.resourceResponse(
                filename, OptionMap.of("root", RESOURCE_ROOT));
        if (response == null) {
            return null;
        }

        // Set Content-Type based on file extension
        String ext = filename.substring(filename.lastIndexOf('.'));
        String contentType = CONTENT_TYPES.get(ext);
        if (contentType != null) {
            HttpResponseUtils.contentType(response, contentType);
        }

        return response;
    }
}
