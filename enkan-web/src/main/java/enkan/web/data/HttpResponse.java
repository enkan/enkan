package enkan.web.data;

import enkan.data.ConversationAvailable;
import enkan.data.FlashAvailable;
import enkan.data.SessionAvailable;
import enkan.data.Traceable;
import enkan.web.collection.Headers;
import enkan.collection.Multimap;

import java.io.File;
import java.io.InputStream;

/**
 * Represents an outgoing HTTP response.
 *
 * <p>A response carries a status code, headers, a body (string, stream, or
 * file), and optional session/flash/conversation side-effects.
 * The three static factory methods cover the most common body types:
 *
 * <pre>{@code
 * HttpResponse.of("Hello, world!")            // plain text
 * HttpResponse.of(inputStream)                // binary / streaming
 * HttpResponse.of(new File("report.pdf"))     // file download
 * }</pre>
 *
 * <p>Status defaults to {@code 200}.  Headers default to an empty
 * {@link enkan.collection.Headers} instance.  Body accessors
 * {@link #getBodyAsString()} and {@link #getBodyAsStream()} let
 * downstream middleware consume the body in either form regardless of how
 * it was set.
 *
 * @author kawasima
 */
public interface HttpResponse extends HasBody, HasStatus, HasHeaders,
        SessionAvailable, FlashAvailable, Traceable, ConversationAvailable {
    /**
     * Creates HttpResponse with the given String body.
     *
     * @param body String body
     * @return the response object
     */
    static HttpResponse of(String body) {
        HttpResponse response = new DefaultHttpResponse(200,
                Headers.empty());
        response.setBody(body);
        return response;
    }

    /**
     * Creates HttpResponse with the given Stream body.
     *
     * @param body Stream body
     * @return the response object
     */
    static HttpResponse of(InputStream body) {
        HttpResponse response = new DefaultHttpResponse(200,
                Headers.empty());
        response.setBody(body);
        return response;
    }

    /**
     * Creates HttpResponse with the given file body.
     *
     * @param body file body
     * @return the response object
     */
    static HttpResponse of(File body) {
        HttpResponse response = new DefaultHttpResponse(200,
                Headers.empty());
        response.setBody(body);
        return response;
    }

    /**
     * Creates HttpResponse with the given StreamingBody.
     *
     * @param body streaming body
     * @return the response object
     */
    static HttpResponse of(StreamingBody body) {
        HttpResponse response = new DefaultHttpResponse(200,
                Headers.empty());
        response.setBody(body);
        return response;
    }


    Multimap<String, Cookie> getCookies();
    void setCookies(Multimap<String, Cookie> cookies);

    default void setContentType(String type) {
        getHeaders().put("Content-Type", type);
    }

    /**
     * Returns body as String.
     *
     * @return the body of this response
     */
    String getBodyAsString();

    /**
     * Returns body as InputStream.
     *
     * @return the body of this response
     */
    InputStream getBodyAsStream();

    void setBody(String body);
    void setBody(InputStream body);
    void setBody(File body);
    void setBody(StreamingBody body);
}
