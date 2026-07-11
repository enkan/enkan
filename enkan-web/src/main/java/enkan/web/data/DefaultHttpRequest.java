package enkan.web.data;

import enkan.data.Session;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;

import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A default implementation for HTTP request.
 *
 * <p>This is a mutable bean populated incrementally: the server adapter sets the
 * transport fields and middleware fill in the rest. A field being {@code null}
 * is meaningful — it marks state that has not been populated yet (e.g. {@code
 * ParamsMiddleware} keys off {@code getParams() == null} to decide whether to
 * parse). Every accessor is therefore {@code @Nullable}.
 *
 * @author kawasima
 */
public class DefaultHttpRequest implements HttpRequest {
    private int serverPort;
    private @Nullable String serverName;
    private @Nullable String remoteAddr;
    private @Nullable String uri;
    private @Nullable String queryString;
    private @Nullable String scheme;
    private @Nullable String requestMethod;
    private @Nullable String protocol;
    private @Nullable Headers headers;
    private @Nullable String contentType;
    private @Nullable Long contentLength;
    private @Nullable String characterEncoding;
    private @Nullable InputStream body;

    private @Nullable Parameters params;
    private @Nullable Parameters formParams;
    private @Nullable Parameters queryParams;

    private @Nullable Session session;
    private @Nullable Map<String, Cookie> cookies;
    private @Nullable Map<String, Object> extensions;

    @Override
    public @Nullable String getUri() {
        return uri;
    }

    @Override
    public void setUri(@Nullable String uri) {
        this.uri = uri;
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }

    @Override
    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    @Override
    public @Nullable String getServerName() {
        return serverName;
    }

    @Override
    public void setServerName(@Nullable String serverName) {
        this.serverName = serverName;
    }

    @Override
    public @Nullable String getRemoteAddr() {
        return remoteAddr;
    }

    @Override
    public void setRemoteAddr(@Nullable String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    @Override
    public @Nullable String getQueryString() {
        return queryString;
    }

    @Override
    public void setQueryString(@Nullable String queryString) {
        this.queryString = queryString;
    }

    @Override
    public @Nullable String getScheme() {
        return scheme;
    }

    @Override
    public void setScheme(@Nullable String scheme) {
        this.scheme = scheme;
    }

    @Override
    public @Nullable String getRequestMethod() {
        return requestMethod;
    }

    @Override
    public void setRequestMethod(@Nullable String requestMethod) {
        this.requestMethod = Optional.ofNullable(requestMethod)
                .map(m -> m.toUpperCase(Locale.ENGLISH))
                .orElse(null);
    }

    @Override
    public @Nullable String getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(@Nullable String protocol) {
        this.protocol = protocol;
    }

    @Override
    public @Nullable Headers getHeaders() {
        return headers;
    }

    @Override
    public void setHeaders(@Nullable Headers headers) {
        this.headers = headers;
    }

    @Override
    public @Nullable String getContentType() {
        return contentType;
    }

    @Override
    public void setContentType(@Nullable String contentType) {
        this.contentType = contentType;
    }

    @Override
    public @Nullable Long getContentLength() {
        return contentLength;
    }

    @Override
    public void setContentLength(@Nullable Long contentLength) {
        this.contentLength = contentLength;
    }

    @Override
    public @Nullable String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(@Nullable String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    @Override
    public @Nullable InputStream getBody() {
        return body;
    }

    @Override
    public void setBody(@Nullable InputStream body) {
        this.body = body;
    }

    @Override
    public @Nullable Parameters getParams() {
        return params;
    }

    @Override
    public void setParams(@Nullable Parameters params) {
        this.params = params;
    }

    @Override
    public @Nullable Parameters getFormParams() {
        return formParams;
    }

    @Override
    public void setFormParams(@Nullable Parameters formParams) {
        this.formParams = formParams;
    }

    @Override
    public @Nullable Parameters getQueryParams() {
        return queryParams;
    }

    @Override
    public void setQueryParams(@Nullable Parameters queryParams) {
        this.queryParams = queryParams;
    }

    @Override
    public @Nullable Map<String, Cookie> getCookies() {
        return cookies;
    }

    @Override
    public void setCookies(@Nullable Map<String, Cookie> cookies) {
        this.cookies = cookies;
    }

    @Override
    public @Nullable Session getSession() {
        return session;
    }

    @Override
    public void setSession(@Nullable Session session) {
        this.session = session;
    }

    @Override
    public <T> void setExtension(String name, @Nullable T extension) {
        if (extensions == null) {
            extensions = new HashMap<>();
        }
        extensions.put(name, extension);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getExtension(String name) {
        if (extensions == null) {
            extensions = new HashMap<>();
        }
        return (T) extensions.get(name);
    }
}
