package enkan.component.jetty.digest;

import enkan.web.http.fields.digest.DigestFields;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Jetty 12 core {@link Handler.Wrapper} that computes the {@code Content-Digest}
 * header on HTTP responses per RFC 9530.
 *
 * <p>This handler is placed <em>outside</em> the {@code CompressionHandler}, so it
 * observes the bytes as they are written to the response <em>after</em> any content
 * encoding (gzip) has been applied. All response bytes are buffered in memory, the
 * digest is computed, the header is set, and then the bytes are forwarded to the
 * underlying response in a single write.
 *
 * <p>Algorithm negotiation via {@code Want-Content-Digest} is supported.
 * Streaming responses that use multiple non-final writes are buffered incrementally
 * and the header is added on the last write.
 *
 * @author kawasima
 */
public class ContentDigestHandler extends Handler.Wrapper {

    private final String defaultAlgorithm;

    /**
     * Creates a handler with the given default digest algorithm.
     *
     * @param defaultAlgorithm the SF algorithm name ({@code "sha-256"} or {@code "sha-512"})
     *                         used when the client does not send {@code Want-Content-Digest}
     */
    public ContentDigestHandler(String defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        // Negotiate algorithm per Want-Content-Digest request header
        String wantValue = request.getHeaders().get("Want-Content-Digest");
        String algorithm = DigestFields.negotiateAlgorithm(wantValue, defaultAlgorithm);

        if (algorithm == null) {
            // Client explicitly opted out of all supported algorithms
            return super.handle(request, response, callback);
        }

        BufferingResponse bufferingResponse = new BufferingResponse(request, response, algorithm);
        return super.handle(request, bufferingResponse, callback);
    }

    // -------------------------------------------------------------------------
    // Inner class: buffering response wrapper
    // -------------------------------------------------------------------------

    /**
     * Wraps {@link Response} to buffer all written bytes. On the final write,
     * computes the {@code Content-Digest} header from the buffered (post-compression)
     * bytes and forwards everything to the underlying response.
     */
    private static final class BufferingResponse extends Response.Wrapper {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
        private final String algorithm;

        BufferingResponse(Request request, Response wrapped, String algorithm) {
            super(request, wrapped);
            this.algorithm = algorithm;
        }

        @Override
        public void write(boolean last, ByteBuffer byteBuffer, Callback callback) {
            if (byteBuffer != null && byteBuffer.hasRemaining()) {
                // Copy bytes without advancing the caller's ByteBuffer position
                ByteBuffer slice = byteBuffer.duplicate();
                int remaining = slice.remaining();
                if (slice.hasArray()) {
                    buffer.write(slice.array(), slice.arrayOffset() + slice.position(), remaining);
                } else {
                    byte[] bytes = new byte[remaining];
                    slice.get(bytes);
                    buffer.write(bytes, 0, bytes.length);
                }
            }

            if (last) {
                byte[] allBytes = buffer.toByteArray();
                String digestHeader = DigestFields.computeDigestHeader(allBytes, algorithm);
                getWrapped().getHeaders().put("Content-Digest", digestHeader);
                // Forward all buffered bytes in a single final write
                getWrapped().write(true, ByteBuffer.wrap(allBytes), callback);
            } else {
                // Accumulating — do not forward yet; signal success to keep the pipeline running
                callback.succeeded();
            }
        }
    }
}
