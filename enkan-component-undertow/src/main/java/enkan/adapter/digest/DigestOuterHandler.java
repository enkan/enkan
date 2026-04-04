package enkan.adapter.digest;

import enkan.web.util.DigestFieldsUtils;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Undertow {@link HttpHandler} placed <em>outside</em> the {@code EncodingHandler}
 * in the handler chain. It registers a {@link DigestConduit} for {@code Content-Digest}
 * before delegating to the next handler.
 *
 * <p>Because {@code addResponseWrapper()} stacks conduits such that the last-registered
 * is outermost (closest to the application), registering the {@code Content-Digest}
 * conduit here — before {@code EncodingHandler} adds its gzip conduit — ensures that
 * the {@code Content-Digest} conduit sits <em>inside</em> (network-side of) the gzip
 * conduit and therefore observes the post-compression bytes.
 *
 * <p>Data flow (compression enabled):
 * <pre>
 *   app → ReprDigestConduit → GzipConduit → ContentDigestConduit → socket
 *          ↑ Repr-Digest                    ↑ Content-Digest
 * </pre>
 *
 * @author kawasima
 */
public class DigestOuterHandler implements HttpHandler {

    private final HttpHandler next;
    private final String defaultAlgorithm;

    public DigestOuterHandler(HttpHandler next, String defaultAlgorithm) {
        this.next = next;
        this.defaultAlgorithm = defaultAlgorithm;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String wantValue = exchange.getRequestHeaders().getFirst("Want-Content-Digest");
        String algorithm = DigestFieldsUtils.negotiateAlgorithm(wantValue, defaultAlgorithm);

        if (algorithm != null) {
            exchange.addResponseWrapper((factory, ex) ->
                    new DigestConduit(factory.create(), ex, algorithm, "Content-Digest"));
        }
        next.handleRequest(exchange);
    }
}
