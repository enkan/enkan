package enkan.web.data;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A response body type that signals the adapter to enter streaming mode.
 *
 * <p>Instead of writing a complete body and closing the response, the adapter
 * flushes the response headers immediately and then delegates to
 * {@link #writeTo(OutputStream)}, which blocks until the stream is finished.
 * With virtual threads this blocking is cheap, making it suitable for
 * long-lived connections such as Server-Sent Events.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * HttpResponse response = HttpResponse.of((StreamingBody) out -> {
 *     out.write("data: hello\n\n".getBytes());
 *     out.flush();
 * });
 * return response;
 * }</pre>
 *
 * @author kawasima
 */
@FunctionalInterface
public interface StreamingBody {

    /**
     * Writes the response body to the given output stream.
     *
     * <p>The implementation controls the lifetime of the stream: it should
     * write and flush events as needed and return when done.  If the client
     * disconnects, the adapter closes the stream, causing an
     * {@link IOException} that breaks the write loop.</p>
     *
     * @param out the raw output stream of the HTTP response
     * @throws IOException if writing fails (typically client disconnect)
     */
    void writeTo(OutputStream out) throws IOException;
}
