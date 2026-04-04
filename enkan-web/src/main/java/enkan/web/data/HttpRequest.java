package enkan.web.data;

import enkan.data.ConversationAvailable;
import enkan.data.FlashAvailable;
import enkan.data.PrincipalAvailable;
import enkan.data.SessionAvailable;
import enkan.data.Traceable;
import enkan.data.UriAvailable;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;

import java.io.InputStream;
import java.util.Map;

/**
 * Represents an incoming HTTP request.
 *
 * <p>This interface aggregates all per-request capabilities through its
 * super-interfaces:
 * <ul>
 *   <li>{@link UriAvailable} — request URI and HTTP method</li>
 *   <li>{@link SessionAvailable} — session access</li>
 *   <li>{@link FlashAvailable} — flash message access</li>
 *   <li>{@link PrincipalAvailable} — authenticated principal</li>
 *   <li>{@link ConversationAvailable} — long-running conversation and its state</li>
 *   <li>{@link Traceable} — distributed trace log</li>
 *   <li>{@link Extendable} — arbitrary named extensions attached by middleware</li>
 * </ul>
 *
 * <p>The default implementation is {@link DefaultHttpRequest}.  Middleware
 * that needs to attach additional capabilities uses
 * {@link enkan.util.MixinUtils#mixin} to return a proxy that also implements
 * the desired extra interfaces (e.g. {@code BodyDeserializable},
 * {@code EntityManageable}).
 *
 * @author kawasima
 */
public interface HttpRequest
        extends UriAvailable, SessionAvailable, FlashAvailable, PrincipalAvailable, ConversationAvailable, Traceable {

    /** Returns the port number on which the request was received. */
    int getServerPort();

    /** Sets the port number on which the request was received. */
    void setServerPort(int serverPort);

    /** Returns the host name of the server that received the request. */
    String getServerName();

    /** Sets the host name of the server that received the request. */
    void setServerName(String serverName);

    /** Returns the IP address of the client that sent the request. */
    String getRemoteAddr();

    /** Sets the IP address of the client that sent the request. */
    void setRemoteAddr(String remoteAddr);

    /** Returns the request URI (path portion, without the query string). */
    String getUri();

    /** Sets the request URI. */
    void setUri(String uri);

    /** Returns the query string portion of the request URL, or {@code null} if none. */
    String getQueryString();

    /** Sets the query string portion of the request URL. */
    void setQueryString(String queryString);

    /** Returns the scheme (e.g. {@code "http"} or {@code "https"}) of the request. */
    String getScheme();

    /** Sets the scheme of the request. */
    void setScheme(String scheme);

    /** Returns the HTTP method (e.g. {@code "GET"}, {@code "POST"}) of the request. */
    String getRequestMethod();

    /** Sets the HTTP method of the request. */
    void setRequestMethod(String requestMethod);

    /** Returns the protocol and version (e.g. {@code "HTTP/1.1"}) of the request. */
    String getProtocol();

    /** Sets the protocol and version of the request. */
    void setProtocol(String protocol);

    /** Returns the HTTP headers associated with this request. */
    Headers getHeaders();

    /** Sets the HTTP headers for this request. */
    void setHeaders(Headers headers);

    /** Returns the MIME type of the request body, or {@code null} if not specified. */
    String getContentType();

    /** Sets the MIME type of the request body. */
    void setContentType(String contentType);

    /** Returns the length of the request body in bytes, or {@code null} if unknown. */
    Long getContentLength();

    /** Sets the length of the request body in bytes. */
    void setContentLength(Long contentLength);

    /** Returns the character encoding of the request body, or {@code null} if not specified. */
    String getCharacterEncoding();

    /** Sets the character encoding of the request body. */
    void setCharacterEncoding(String characterEncoding);

    /** Returns the request body as an {@link InputStream}. */
    InputStream getBody();

    /** Sets the request body. */
    void setBody(InputStream body);

    /** Returns the merged request parameters (query + form + path). */
    Parameters getParams();

    /** Sets the merged request parameters. */
    void setParams(Parameters params);

    /** Returns the form (POST body) parameters. */
    Parameters getFormParams();

    /** Sets the form parameters. */
    void setFormParams(Parameters formParams);

    /** Returns the query string parameters. */
    Parameters getQueryParams();

    /** Sets the query string parameters. */
    void setQueryParams(Parameters queryParams);

    /** Returns the cookies sent with this request, keyed by cookie name. */
    Map<String, Cookie> getCookies();

    /** Sets the cookies for this request. */
    void setCookies(Map<String, Cookie> cookies);

    /**
     * Attaches an arbitrary extension object to this request.
     *
     * @param name      the extension name
     * @param extension the extension object
     * @param <T>       the extension type
     */
    <T> void setExtension(String name, T extension);

    /**
     * Retrieves an extension object previously attached to this request.
     *
     * @param name the extension name
     * @param <T>  the expected extension type
     * @return the extension object, or {@code null} if not present
     */
    <T> T getExtension(String name);
}
