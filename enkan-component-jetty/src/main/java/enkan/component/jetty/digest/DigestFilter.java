package enkan.component.jetty.digest;

import enkan.web.http.fields.digest.DigestFields;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Servlet Filter that computes {@code Repr-Digest} (and {@code Content-Digest} when
 * compression is disabled) on HTTP responses per RFC 9530.
 *
 * <p>This filter is placed inside the servlet context handler, <em>before</em> any
 * {@code CompressionHandler}. It therefore observes the pre-encoding response bytes,
 * which are exactly the representation bytes needed for {@code Repr-Digest}.
 *
 * <p>When compression is disabled, the bytes on the wire equal the representation
 * bytes, so both {@code Repr-Digest} and {@code Content-Digest} are set to the same
 * value.
 *
 * <p>Algorithm negotiation via {@code Want-Repr-Digest} is supported.
 *
 * <p><strong>Buffering behaviour:</strong> All response bytes are captured in memory
 * before being forwarded to the underlying response. This includes
 * {@link enkan.web.data.StreamingBody} responses — the body is fully buffered so that
 * the digest can be computed before headers are committed.
 * {@code flushBuffer()} calls from the application are suppressed to keep headers
 * mutable until after the digest is set. Non-blocking writes
 * ({@link jakarta.servlet.WriteListener}) are not supported and will throw
 * {@link UnsupportedOperationException}.
 *
 * @author kawasima
 */
public class DigestFilter implements Filter {

    /** Init-param name for the default digest algorithm. */
    public static final String PARAM_ALGORITHM = "digestAlgorithm";
    /**
     * Init-param name indicating whether a {@code CompressionHandler} is active in the chain.
     *
     * <p>When {@code true}, this filter only computes {@code Repr-Digest} (pre-compression bytes).
     * {@code Content-Digest} (post-compression bytes) is handled by {@code ContentDigestHandler}
     * which is placed outside the {@code CompressionHandler}. The value must be kept in sync
     * with the {@code compress?} option passed to {@code JettyAdapter}.
     */
    public static final String PARAM_COMPRESSED = "compressed";

    private String defaultAlgorithm;
    private boolean compressed;

    @Override
    public void init(FilterConfig config) {
        defaultAlgorithm = config.getInitParameter(PARAM_ALGORITHM);
        compressed = Boolean.parseBoolean(config.getInitParameter(PARAM_COMPRESSED));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpReq) ||
            !(response instanceof HttpServletResponse httpResp)) {
            chain.doFilter(request, response);
            return;
        }

        // Negotiate algorithm per Want-Repr-Digest header
        String wantReprDigest = httpReq.getHeader("Want-Repr-Digest");
        String reprAlgorithm  = DigestFields.negotiateAlgorithm(wantReprDigest, defaultAlgorithm);

        String contentAlgorithm = null;
        if (!compressed) {
            String wantContentDigest = httpReq.getHeader("Want-Content-Digest");
            contentAlgorithm = DigestFields.negotiateAlgorithm(wantContentDigest, defaultAlgorithm);
        }

        if (reprAlgorithm == null && contentAlgorithm == null) {
            // Client explicitly opted out of all supported algorithms
            chain.doFilter(request, response);
            return;
        }

        BufferingResponseWrapper wrapper = new BufferingResponseWrapper(httpResp);
        chain.doFilter(request, wrapper);

        byte[] body = wrapper.getBuffer();

        if (reprAlgorithm != null) {
            httpResp.setHeader("Repr-Digest", DigestFields.computeDigestHeader(body, reprAlgorithm));
        }
        if (contentAlgorithm != null) {
            // No compression: Content-Digest == Repr-Digest (or different algorithm if negotiated)
            String header = contentAlgorithm.equals(reprAlgorithm)
                    ? httpResp.getHeader("Repr-Digest")
                    : DigestFields.computeDigestHeader(body, contentAlgorithm);
            httpResp.setHeader("Content-Digest", header);
        }

        // Write buffered body to the actual response
        httpResp.getOutputStream().write(body);
    }

    // -------------------------------------------------------------------------
    // Inner class: buffering response wrapper
    // -------------------------------------------------------------------------

    /**
     * Wraps {@link HttpServletResponse} to capture all bytes written to the
     * output stream or writer into an in-memory buffer, without forwarding them
     * to the underlying response until explicitly flushed.
     */
    static final class BufferingResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(4096);
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        BufferingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        byte[] getBuffer() {
            if (writer != null) writer.flush();
            return buffer.toByteArray();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IllegalStateException {
            if (writer != null) {
                throw new IllegalStateException("getWriter() has already been called");
            }
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() { return true; }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        throw new UnsupportedOperationException("Non-blocking writes are not supported");
                    }

                    @Override
                    public void write(int b) {
                        buffer.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        buffer.write(b, off, len);
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (outputStream != null) {
                throw new IllegalStateException("getOutputStream() has already been called");
            }
            if (writer == null) {
                writer = new PrintWriter(buffer, false, getCharacterEncoding() != null
                        ? java.nio.charset.Charset.forName(getCharacterEncoding())
                        : java.nio.charset.StandardCharsets.ISO_8859_1);
            }
            return writer;
        }

        @Override
        public void flushBuffer() {
            // Intentionally suppress commit: the Servlet spec makes response headers
            // immutable once flushBuffer() commits the response. Suppressing it keeps
            // headers mutable so that Repr-Digest / Content-Digest can be set after
            // chain.doFilter() returns.
            // Side-effect: streaming / chunked responses that rely on early flush are
            // fully buffered. This is a known trade-off for digest computation.
        }

        @Override
        public void resetBuffer() {
            buffer.reset();
        }
    }
}
