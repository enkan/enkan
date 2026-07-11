package enkan.web.data;

import enkan.data.ConversationAvailable;
import enkan.data.FlashAvailable;
import enkan.data.PrincipalAvailable;
import enkan.data.SessionAvailable;
import enkan.data.Traceable;
import enkan.data.UriAvailable;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;

import org.jspecify.annotations.Nullable;

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
    @Nullable String getServerName();

    /** Sets the host name of the server that received the request. */
    void setServerName(@Nullable String serverName);

    /** Returns the IP address of the client that sent the request. */
    @Nullable String getRemoteAddr();

    /** Sets the IP address of the client that sent the request. */
    void setRemoteAddr(@Nullable String remoteAddr);

    /** Returns the request URI (path portion, without the query string), or {@code null} until set. */
    @Nullable String getUri();

    /** Sets the request URI. */
    void setUri(@Nullable String uri);

    /** Returns the query string portion of the request URL, or {@code null} if none. */
    @Nullable String getQueryString();

    /** Sets the query string portion of the request URL. */
    void setQueryString(@Nullable String queryString);

    /** Returns the scheme (e.g. {@code "http"} or {@code "https"}) of the request. */
    @Nullable String getScheme();

    /** Sets the scheme of the request. */
    void setScheme(@Nullable String scheme);

    /** Returns the HTTP method (e.g. {@code "GET"}, {@code "POST"}) of the request, or {@code null} until set. */
    @Nullable String getRequestMethod();

    /** Sets the HTTP method of the request. */
    void setRequestMethod(@Nullable String requestMethod);

    /** Returns the protocol and version (e.g. {@code "HTTP/1.1"}) of the request. */
    @Nullable String getProtocol();

    /** Sets the protocol and version of the request. */
    void setProtocol(@Nullable String protocol);

    /** Returns the HTTP headers associated with this request. */
    @Nullable Headers getHeaders();

    /** Sets the HTTP headers for this request. */
    void setHeaders(@Nullable Headers headers);

    /** Returns the MIME type of the request body, or {@code null} if not specified. */
    @Nullable String getContentType();

    /** Sets the MIME type of the request body. */
    void setContentType(@Nullable String contentType);

    /** Returns the length of the request body in bytes, or {@code null} if unknown. */
    @Nullable Long getContentLength();

    /** Sets the length of the request body in bytes. */
    void setContentLength(@Nullable Long contentLength);

    /** Returns the character encoding of the request body, or {@code null} if not specified. */
    @Nullable String getCharacterEncoding();

    /** Sets the character encoding of the request body. */
    void setCharacterEncoding(@Nullable String characterEncoding);

    /** Returns the request body as an {@link InputStream}, or {@code null} if absent. */
    @Nullable InputStream getBody();

    /** Sets the request body. */
    void setBody(@Nullable InputStream body);

    /** Returns the merged request parameters (query + form + path), or {@code null} until parsed. */
    @Nullable Parameters getParams();

    /** Sets the merged request parameters. */
    void setParams(@Nullable Parameters params);

    /** Returns the form (POST body) parameters, or {@code null} until parsed. */
    @Nullable Parameters getFormParams();

    /** Sets the form parameters. */
    void setFormParams(@Nullable Parameters formParams);

    /** Returns the query string parameters, or {@code null} until parsed. */
    @Nullable Parameters getQueryParams();

    /** Sets the query string parameters. */
    void setQueryParams(@Nullable Parameters queryParams);

    /** Returns the cookies sent with this request keyed by cookie name, or {@code null} until parsed. */
    @Nullable Map<String, Cookie> getCookies();

    /** Sets the cookies for this request. */
    void setCookies(@Nullable Map<String, Cookie> cookies);

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
    <T> @Nullable T getExtension(String name);
}
