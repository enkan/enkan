package enkan.component.undertow.digest;

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
 * XNIO {@link StreamSinkConduit} that buffers the response body, computes a
 * digest, sets the specified response header, then forwards all buffered bytes
 * to the underlying conduit in one terminal write.
 *
 * <p>Used by the Undertow digest support for both {@code Repr-Digest} (placed
 * after the application, before any compression conduit) and {@code Content-Digest}
 * (placed before the compression conduit, so it observes the compressed bytes).
 *
 * @author kawasima
 */
public class DigestConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final HttpServerExchange exchange;
    private final String algorithm;
    private final HttpString header;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);

    /**
     * @param next       the underlying conduit
     * @param exchange   the in-flight HTTP exchange (used to set response headers)
     * @param algorithm  the SF algorithm name ({@code "sha-256"} or {@code "sha-512"})
     * @param header     the response header to set (e.g. {@code "Repr-Digest"} or {@code "Content-Digest"})
     */
    public DigestConduit(StreamSinkConduit next, HttpServerExchange exchange, String algorithm, HttpString header) {
        super(next);
        this.exchange = exchange;
        this.algorithm = algorithm;
        this.header = header;
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

    // writeFinal only buffers the last chunk — terminateWrites() is called separately
    // by the framework (HttpServerExchange.endExchange). Calling terminateWrites() here
    // would cause a double-invocation: header set twice and buffered bytes forwarded twice.
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
            // Cap the read to the remaining requested bytes so we never buffer
            // more than count bytes total.
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
    // On terminateWrites: compute digest, set header, flush, terminate
    // -------------------------------------------------------------------------

    @Override
    public void terminateWrites() throws IOException {
        byte[] allBytes = buffer.toByteArray();
        String digestValue = DigestFieldsUtils.computeDigestHeader(allBytes, algorithm);
        exchange.getResponseHeaders().put(header, digestValue);

        // Write all buffered bytes to the underlying conduit, then terminate.
        // The underlying conduit may accept fewer bytes than requested (partial write);
        // use awaitWritable() to block until space is available rather than breaking early,
        // which would truncate the response body.
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
