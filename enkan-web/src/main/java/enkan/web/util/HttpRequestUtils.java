package enkan.web.util;

import enkan.web.data.HttpRequest;

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
        StringBuilder sb = new StringBuilder()
                .append(request.getScheme())
                .append("://")
                .append(request.getHeaders().get("host"))
                .append(request.getUri());
        String queryString = request.getQueryString();
        if (queryString != null) {
            sb.append('?').append(queryString);
        }

        return sb.toString();
    }

    public static String contentType(HttpRequest request) {
        String type = some(request.getHeaders(), headers -> headers.get("content-type")).orElse(null);
        if (type == null) return null;

        Matcher m = CONTENT_TYPE_PATTERN.matcher(type);
        return m.find() ? m.group(1) : null;
    }

    public static Long contentLength(HttpRequest request) {
        String length = request.getHeaders().get("content-length");
        if (length != null) {
            try {
                return Long.parseLong(length, 10);
            } catch (NumberFormatException _) {
                // ignore
            }
        }
        return null;
    }

    public static String characterEncoding(HttpRequest request) {
        String type = request.getHeaders().get("content-type");
        if (type == null) return null;

        Matcher m = CHARSET_PATTERN.matcher(type);
        return m.find() ? m.group(1) : null;
    }

    public static String pathInfo(HttpRequest request) {
        return request.getUri();
    }

    public static boolean isUrlEncodedForm(HttpRequest request) {
        String type = contentType(request);
        return type != null && type.startsWith("application/x-www-form-urlencoded");
    }
}
