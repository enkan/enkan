package enkan.web.data;

import enkan.data.Session;
import enkan.web.collection.Headers;
import enkan.collection.Parameters;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A default implementation for HTTP request
 *
 * @author kawasima
 */
public class DefaultHttpRequest implements HttpRequest {
    private int serverPort;
    private String serverName;
    private String remoteAddr;
    private String uri;
    private String queryString;
    private String scheme;
    private String requestMethod;
    private String protocol;
    private Headers headers;
    private String contentType;
    private Long contentLength;
    private String characterEncoding;
    private InputStream body;

    private Parameters params;
    private Parameters formParams;
    private Parameters queryParams;

    private Session session;
    private Map<String, Cookie> cookies;
    private Map<String, Object> extensions;

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public void setUri(String uri) {
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
    public String getServerName() {
        return serverName;
    }

    @Override
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    @Override
    public String getRemoteAddr() {
        return remoteAddr;
    }

    @Override
    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String getRequestMethod() {
        return requestMethod;
    }

    @Override
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = Optional.ofNullable(requestMethod)
                .map(m -> m.toUpperCase(Locale.ENGLISH))
                .orElse(null);
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public Headers getHeaders() {
        return headers;
    }

    @Override
    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public Long getContentLength() {
        return contentLength;
    }

    @Override
    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    @Override
    public InputStream getBody() {
        return body;
    }

    @Override
    public void setBody(InputStream body) {
        this.body = body;
    }

    @Override
    public Parameters getParams() {
        return params;
    }

    @Override
    public void setParams(Parameters params) {
        this.params = params;
    }

    @Override
    public Parameters getFormParams() {
        return formParams;
    }

    @Override
    public void setFormParams(Parameters formParams) {
        this.formParams = formParams;
    }

    @Override
    public Parameters getQueryParams() {
        return queryParams;
    }

    @Override
    public void setQueryParams(Parameters queryParams) {
        this.queryParams = queryParams;
    }

    @Override
    public Map<String, Cookie> getCookies() {
        return cookies;
    }

    @Override
    public void setCookies(Map<String, Cookie> cookies) {
        this.cookies = cookies;
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public <T> void setExtension(String name, T extension) {
        if (extensions == null) {
            extensions = new HashMap<>();
        }
        extensions.put(name, extension);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getExtension(String name) {
        if (extensions == null) {
            extensions = new HashMap<>();
        }
        return (T) extensions.get(name);
    }
}
