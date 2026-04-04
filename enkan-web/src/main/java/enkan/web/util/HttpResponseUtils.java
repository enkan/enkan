package enkan.web.util;

import enkan.web.collection.Headers;
import enkan.collection.OptionMap;
import enkan.web.data.HttpResponse;
import enkan.exception.FalteringEnvironmentException;
import enkan.exception.UnreachableException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static enkan.util.BeanBuilder.*;

/**
 * Utilities for HTTP response.
 *
 * @author kawasima
 */
public class HttpResponseUtils {
    /**
     * HTTP redirect status codes.
     *
     * <p>For 301 and 302, RFC 7231 allows user agents to change a POST request to a GET when following
     * the redirect, and browsers commonly do so in practice (RFC 7231 §6.4.2–6.4.3). Status code 303
     * explicitly instructs the user agent to perform a GET (or HEAD) when following the redirect
     * (RFC 7231 §6.4.4), and is typically used for POST-redirect-GET flows after form submission.
     *
     * <p>Codes 307 and 308 are <em>method-preserving</em>: the request method and body MUST NOT be changed
     * (RFC 7231 §6.4.7 and RFC 7538 §3). Use these when the original method (e.g. POST, PUT) must be
     * repeated at the new URI.
     */
    public enum RedirectStatusCode {
        /**
         * 301 Moved Permanently (RFC 7231 §6.4.2).
         * RFC 7231 permits user agents to change a POST to GET when following the redirect;
         * browsers commonly do so in practice.
         * Use {@link #PERMANENT_REDIRECT} (308) when the original method must be preserved.
         */
        MOVED_PERMANENTLY(301),

        /**
         * 302 Found (RFC 7231 §6.4.3).
         * RFC 7231 permits user agents to change a POST to GET when following the redirect;
         * browsers commonly do so in practice.
         * Use {@link #TEMPORARY_REDIRECT} (307) when the original method must be preserved.
         */
        FOUND(302),

        /**
         * 303 See Other (RFC 7231 §6.4.4).
         * The response to the redirect MUST be retrieved with GET or HEAD.
         * Commonly used after a POST form submission to redirect to a result page.
         */
        SEE_OTHER(303),

        /**
         * 307 Temporary Redirect (RFC 7231 §6.4.7).
         * The request method and body MUST NOT be changed when following the redirect.
         * Use this instead of 302 when method preservation is required.
         */
        TEMPORARY_REDIRECT(307),

        /**
         * 308 Permanent Redirect (RFC 7538 §3).
         * The request method and body MUST NOT be changed when following the redirect.
         * Use this instead of 301 when method preservation is required.
         */
        PERMANENT_REDIRECT(308);

        private final int code;

        RedirectStatusCode(int statusCode) {
            this.code = statusCode;
        }

        public int getStatusCode() { return code; }
    }


    /**
     * Creates a redirect response with a {@code Location} header.
     *
     * <p><strong>Method-change warning:</strong> for 301 and 302, browsers commonly change a POST
     * request to GET when following the redirect (RFC 7231 §6.4.2–6.4.3). For 303, the user agent
     * MUST follow with a GET or HEAD (RFC 7231 §6.4.4). If the original method (e.g. POST or PUT)
     * must be repeated at the new URI, use {@link RedirectStatusCode#TEMPORARY_REDIRECT} (307) or
     * {@link RedirectStatusCode#PERMANENT_REDIRECT} (308) instead.
     *
     * @param url   the target URI to redirect to (set as the {@code Location} header value)
     * @param code  the redirect status code; see {@link RedirectStatusCode} for semantics
     * @return a response with the given status code and a {@code Location} header
     */
    public static HttpResponse redirect(String url, RedirectStatusCode code) {
        return builder(HttpResponse.of(""))
                .set(HttpResponse::setStatus, code.getStatusCode())
                .set(HttpResponse::setHeaders, Headers.of("Location", url))
                .build();
    }

    /**
     * Create a response with a string body.
     *
     * @param body string body
     * @return response
     */
    public static HttpResponse response(String body) {
        return builder(HttpResponse.of(body))
                .set(HttpResponse::setStatus, 200)
                .build();
    }


    /**
     * Returns the header value of the given response.
     *
     * @param response a response object
     * @param name the name of the header
     * @param <T>  the type of the header value
     * @return the header value
     */
    @SuppressWarnings("unchecked")
    public static <T> T getHeader(HttpResponse response, String name) {
        return (T) response.getHeaders().get(name);
    }

    /**
     * Sets a response header with the given name and value
     *
     * @param response a response object
     * @param name the name of the header
     * @param value the header value
     */
    public static void header(HttpResponse response, String name, String value) {
        response.getHeaders().put(name, value);
    }

    /**
     * Sets a charset to the response.
     *
     * @param response a response object
     * @param charset the name of the character set
     */
    public static void charset(HttpResponse response, String charset) {
        String type = getHeader(response, "Content-Type");
        if (type == null) {
            type = "text/plain";
        }
        // Rebuild Content-Type preserving non-charset parameters, then append charset.
        // Uses a state machine to correctly handle semicolons inside quoted-string values
        // (e.g., profile="a;b") without allocating a regex pattern on the hot path.
        int semicolonIndex = type.indexOf(';');
        StringBuilder sb = semicolonIndex < 0
                ? new StringBuilder(type.trim())
                : new StringBuilder(type.substring(0, semicolonIndex).trim());
        if (semicolonIndex >= 0) {
            String paramsPart = type.substring(semicolonIndex + 1);
            StringBuilder currentParam = new StringBuilder();
            boolean inQuotes = false;
            boolean escape = false;
            for (int i = 0; i < paramsPart.length(); i++) {
                char c = paramsPart.charAt(i);
                if (escape) {
                    currentParam.append(c);
                    escape = false;
                    continue;
                }
                if (c == '\\' && inQuotes) {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    inQuotes = !inQuotes;
                    currentParam.append(c);
                    continue;
                }
                if (c == ';' && !inQuotes) {
                    String param = currentParam.toString().trim();
                    if (!param.isEmpty() && !param.regionMatches(true, 0, "charset=", 0, 8)) {
                        sb.append("; ").append(param);
                    }
                    currentParam.setLength(0);
                } else {
                    currentParam.append(c);
                }
            }
            String lastParam = currentParam.toString().trim();
            if (!lastParam.isEmpty() && !lastParam.regionMatches(true, 0, "charset=", 0, 8)) {
                sb.append("; ").append(lastParam);
            }
        }
        sb.append("; charset=").append(charset);
        response.getHeaders().remove("Content-Type");
        header(response, "Content-Type", sb.toString());
    }

    /**
     * Sets a content type to the response.
     *
     * @param response a response object
     * @param type the type of the content
     * @return a HttpResponse contains content-type header
     */
    public static HttpResponse contentType(HttpResponse response, String type) {
        if (type != null) {
            response.getHeaders().remove("Content-Type");
            response.getHeaders().put("Content-Type", type);
        }
        return response;
    }

    /**
     * Sets a size of the response message.
     *
     * @param response a response object
     * @param len the length of response message
     * @return a HttpResponse contains content-length header
     */
    public static HttpResponse contentLength(HttpResponse response, Long len) {
        if (len != null) {
            response.getHeaders().remove("Content-Length");
            response.getHeaders().put("Content-Length", len);
        }
        return response;
    }

    /**
     * Sets the {@code Last-Modified} header on the response.
     * Does nothing if {@code lastModified} is {@code null}.
     *
     * @param response     a response object
     * @param lastModified the last-modified date
     * @return the same response object
     */
    public static HttpResponse lastModified(HttpResponse response, Date lastModified) {
        if (lastModified != null) {
            response.getHeaders().remove("Last-Modified");
            response.getHeaders().put("Last-Modified", HttpDateFormat.RFC1123.format(lastModified));
        }
        return response;
    }

    private static String addEndingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * Returns {@code true} if the JAR entry pointed to by the connection is a directory.
     *
     * @param conn a JAR URL connection
     * @return {@code true} if the entry is a directory
     * @throws IOException if the JAR file cannot be opened
     */
    public static boolean isJarDirectory(JarURLConnection conn) throws IOException {
        JarFile jarFile = conn.getJarFile();
        String entryName = conn.getEntryName();
        ZipEntry dirEntry = jarFile.getEntry(addEndingSlash(entryName));
        return dirEntry != null && dirEntry.isDirectory();
    }

    /**
     * Returns the content length of the connection, or {@code null} if unknown.
     *
     * @param conn a URL connection
     * @return content length in bytes, or {@code null}
     */
    public static Long connectionContentLength(URLConnection conn) {
        long len = conn.getContentLengthLong();
        return len <= 0 ? null : len;
    }

    /**
     * Returns the last-modified date of the connection, or {@code null} if unavailable.
     *
     * @param conn a URL connection
     * @return last-modified date, or {@code null}
     */
    public static Date connectionLastModified(URLConnection conn) {
        long lastMod = conn.getLastModified();
        return lastMod > 0 ? new Date(lastMod) : null;
    }

    /**
     * Returns the {@link ContentData} for the given URL, or {@code null} if the URL
     * points to a directory or an unsupported protocol.
     *
     * @param url the resource URL
     * @return content data, or {@code null}
     */
    public static ContentData<?> resourceData(URL url) {
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            try {
                File file = new File(url.toURI());
                if (!file.isDirectory()) {
                    return new FileContentData(file, file.length(),
                            new Date((file.lastModified() / 1000) * 1000));
                }
            } catch (URISyntaxException e) {
                throw new UnreachableException(e);
            }
        } else if ("jar".equals(protocol)) {
            try {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                if (connection != null && !isJarDirectory(connection)) {
                    return new StreamContentData(connection.getInputStream(),
                            connectionContentLength(connection),
                            connectionLastModified(connection));
                }
            } catch (IOException e) {
                throw new FalteringEnvironmentException(e);
            }
        }

        return null;
    }

    /**
     * Creates an HTTP response from the resource at the given URL.
     *
     * @param url the resource URL
     * @return response, or {@code null} if the URL points to a directory
     */
    public static HttpResponse urlResponse(URL url) {
        ContentData<?> data = resourceData(url);
        if (data == null) return null;

        HttpResponse response = data.toHttpResponse();
        contentLength(response, data.getContentLength());
        lastModified(response, data.getLastModifiedDate());
        return response;
    }

    /**
     * Creates an HTTP response by loading a classpath resource.
     * The resource path is resolved relative to the {@code root} option.
     * If a {@code loader} option is provided it is used; otherwise the
     * thread context class loader is used.
     *
     * @param path    the resource path
     * @param options options map accepting {@code root} (String) and {@code loader} (ClassLoader)
     * @return response, or {@code null} if the resource is not found
     */
    public static HttpResponse resourceResponse(String path, OptionMap options) {
        String root = options.getString("root");
        path = (root != null ? root : "") + "/" + path;
        path = path.replace("//", "/").replaceAll("^/", "");

        ClassLoader loader = (ClassLoader) options.get("loader");
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        URL url = loader.getResource(path);
        return url != null ? urlResponse(url) : null;
    }

    /**
     * Returns {@code true} if the response body is empty (null or empty string).
     *
     * @param response a response object
     * @return {@code true} if the body is empty
     */
    public static boolean isEmptyBody(HttpResponse response) {
        Object body = response.getBody();
        return (body == null || (body instanceof String s && s.isEmpty()));
    }

    private static abstract class ContentData<T> implements Serializable {
        private final T content;
        private final Long contentLength;
        private final Date lastModifiedDate;

        public ContentData(T content, Long contentLength, Date lastModifiedDate) {
            this.content = content;
            this.contentLength = contentLength;
            this.lastModifiedDate = lastModifiedDate;
        }

        protected T getContent() {
            return content;
        }

        public Long getContentLength() {
            return contentLength;
        }

        public Date getLastModifiedDate() {
            return lastModifiedDate;
        }

        public abstract HttpResponse toHttpResponse();
    }

    private static class FileContentData extends ContentData<File> {
        public FileContentData(File content, Long contentLength, Date lastModifiedDate) {
            super(content, contentLength, lastModifiedDate);
        }

        @Override
        public HttpResponse toHttpResponse() {
            return HttpResponse.of(getContent());
        }
    }

    private static class StreamContentData extends ContentData<InputStream> {
        public StreamContentData(InputStream content, Long contentLength, Date lastModifiedDate) {
            super(content, contentLength, lastModifiedDate);
        }

        @Override
        public HttpResponse toHttpResponse() {
            return HttpResponse.of(getContent());
        }
    }

}
