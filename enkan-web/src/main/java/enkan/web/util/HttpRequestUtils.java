package enkan.web.util;

import enkan.web.collection.Headers;
import enkan.web.data.HttpRequest;

import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static enkan.web.util.ParsingUtils.RE_VALUE;
import static enkan.util.ThreadingUtils.some;

/**
 * Functions for augmenting and pulling information from HttpRequest.
 *
 * @author kawasima
 */
public class HttpRequestUtils {

    private static final Pattern CHARSET_PATTERN = Pattern.compile(";(?:.*\\s)?(?i:charset)=(" + RE_VALUE + ")\\s*(?:;|$)");
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("^(.*?)(?:;|$)");
    public static String requestUrl(HttpRequest request) {
        Headers headers = request.getHeaders();
        StringBuilder sb = new StringBuilder()
                .append(request.getScheme())
                .append("://")
                .append(headers != null ? headers.get("host") : null)
                .append(request.getUri());
        String queryString = request.getQueryString();
        if (queryString != null) {
            sb.append('?').append(queryString);
        }

        return sb.toString();
    }

    public static @Nullable String contentType(HttpRequest request) {
        String type = some(request.getHeaders(), headers -> headers.get("content-type")).orElse(null);
        if (type == null) return null;

        Matcher m = CONTENT_TYPE_PATTERN.matcher(type);
        return m.find() ? m.group(1) : null;
    }

    public static @Nullable Long contentLength(HttpRequest request) {
        Headers headers = request.getHeaders();
        String length = headers != null ? headers.get("content-length") : null;
        if (length != null) {
            try {
                return Long.parseLong(length, 10);
            } catch (NumberFormatException _) {
                // ignore
            }
        }
        return null;
    }

    public static @Nullable String characterEncoding(HttpRequest request) {
        Headers headers = request.getHeaders();
        String type = headers != null ? headers.get("content-type") : null;
        if (type == null) return null;

        Matcher m = CHARSET_PATTERN.matcher(type);
        return m.find() ? m.group(1) : null;
    }

    public static @Nullable String pathInfo(HttpRequest request) {
        return request.getUri();
    }

    public static boolean isUrlEncodedForm(HttpRequest request) {
        String type = contentType(request);
        return type != null && type.startsWith("application/x-www-form-urlencoded");
    }
}
