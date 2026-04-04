package enkan.adapter.digest;

import enkan.web.util.DigestFieldsUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * XNIO {@link StreamSinkConduit} that buffers the entire response body once and
 * sets both {@code Repr-Digest} and {@code Content-Digest} from the same byte array.
 *
 * <p>Used when compression is disabled: in that case the on-wire bytes equal the
 * representation bytes, so both headers can be derived from a single buffering pass,
 * avoiding the double-buffer overhead of stacking two {@link DigestConduit}s.
 *
 * @author kawasima
 */
public class CombinedDigestConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private static final HttpString REPR_DIGEST    = HttpString.tryFromString("Repr-Digest");
    private static final HttpString CONTENT_DIGEST = HttpString.tryFromString("Content-Digest");

    private final HttpServerExchange exchange;
    private final String reprAlgorithm;
    private final String contentAlgorithm;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);

    /**
     * @param next             the underlying conduit
     * @param exchange         the in-flight HTTP exchange
     * @param reprAlgorithm    algorithm for {@code Repr-Digest}, or {@code null} to omit
     * @param contentAlgorithm algorithm for {@code Content-Digest}, or {@code null} to omit
     */
    public CombinedDigestConduit(StreamSinkConduit next, HttpServerExchange exchange,
                                 String reprAlgorithm, String contentAlgorithm) {
        super(next);
        this.exchange = exchange;
        this.reprAlgorithm = reprAlgorithm;
        this.contentAlgorithm = contentAlgorithm;
    }

    // -------------------------------------------------------------------------
    // Intercept all writes — buffer without forwarding
    // -------------------------------------------------------------------------

    @Override
    public int write(ByteBuffer src) throws IOException {
        int remaining = src.remaining();
        if (src.hasArray()) {
            buffer.write(src.array(), src.arrayOffset() + src.position(), remaining);
            src.position(src.position() + remaining);
        } else {
            byte[] bytes = new byte[remaining];
            src.get(bytes);
            buffer.write(bytes, 0, bytes.length);
        }
        return remaining;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += write(srcs[i]);
        }
        return total;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return write(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return write(srcs, offset, length);
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) Math.min(count, 65536));
        long transferred = 0;
        while (transferred < count) {
            buf.clear();
            int maxRead = (int) Math.min(buf.capacity(), count - transferred);
            buf.limit(maxRead);
            int read = src.read(buf, position + transferred);
            if (read <= 0) break;
            buf.flip();
            transferred += write(buf);
        }
        return transferred;
    }

    // -------------------------------------------------------------------------
    // On terminateWrites: compute digests, set headers, flush, terminate
    // -------------------------------------------------------------------------

    @Override
    public void terminateWrites() throws IOException {
        byte[] allBytes = buffer.toByteArray();

        if (reprAlgorithm != null) {
            exchange.getResponseHeaders().put(REPR_DIGEST,
                    DigestFieldsUtils.computeDigestHeader(allBytes, reprAlgorithm));
        }
        if (contentAlgorithm != null) {
            // When algorithms are identical, reuse the already-computed header value
            String contentValue = contentAlgorithm.equals(reprAlgorithm)
                    ? exchange.getResponseHeaders().getFirst(REPR_DIGEST)
                    : DigestFieldsUtils.computeDigestHeader(allBytes, contentAlgorithm);
            exchange.getResponseHeaders().put(CONTENT_DIGEST, contentValue);
        }

        if (allBytes.length > 0) {
            ByteBuffer buf = ByteBuffer.wrap(allBytes);
            while (buf.hasRemaining()) {
                int n = super.write(buf);
                if (n == 0) {
                    super.awaitWritable();
                }
            }
        }
        super.terminateWrites();
    }
}
