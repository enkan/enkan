package enkan.middleware;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.collection.Multimap;
import enkan.data.Cookie;
import enkan.data.HostCookie;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.data.SecureCookie;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static enkan.util.ParsingUtils.*;

/**
 * Middleware for parsing or formatting http cookies.
 *
 * @author kawasima
 */
@Middleware(name = "cookies")
public class CookiesMiddleware implements WebMiddleware {
    // RFC 6265 §4.1.1: cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E
    // Backslash (%x5C) and double-quote (%x22) are explicitly excluded.
    private static final Pattern RE_COOKIE_OCTET = Pattern.compile("[\\x21\\x23-\\x2B\\x2D-\\x3A\\x3C-\\x5B\\x5D-\\x7E]");
    private static final Pattern RE_COOKIE_VALUE = Pattern.compile("\"" + RE_COOKIE_OCTET.pattern() + "*\"|" + RE_COOKIE_OCTET.pattern() + "*");
    // Require the value to be followed by a valid delimiter or end-of-input so that
    // non cookie-octet chars after the value (e.g. backslash) prevent a match entirely.
    private static final Pattern RE_COOKIE = Pattern.compile("\\s*(" + RE_TOKEN + ")=(" + RE_COOKIE_VALUE.pattern() + ")\\s*(?:[;,]|$)");

    /**
     * Strip quotes from argument string.
     *
     * @param value a String contains quote character
     * @return a stripped string
     */
    protected String stripQuotes(String value) {
        return value.replaceAll("^\"|\"$", "");
    }

    /**
     * Parses the {@code Cookie} request header and returns a map of cookie name to
     * {@link Cookie} objects.
     *
     * @param request the incoming HTTP request
     * @return a map of parsed cookies; empty if the header is absent
     */
    protected Map<String, Cookie> parseCookies(HttpRequest request) {
        String cookieHeader = request.getHeaders().get("cookie");
        if (cookieHeader == null) {
            return Map.of();
        }

        Map<String, Cookie> cookies = new HashMap<>();
        Matcher m = RE_COOKIE.matcher(cookieHeader);
        while (m.find()) {
            String rawName = m.group(1);
            String value = stripQuotes(m.group(2));
            Cookie cookie;
            String key;
            if (rawName.startsWith("__Host-")) {
                cookie = HostCookie.parse(rawName, value);
                key = cookie.getName();
            } else if (rawName.startsWith("__Secure-")) {
                cookie = SecureCookie.parse(rawName, value);
                key = cookie.getName();
            } else {
                cookie = Cookie.create(rawName, value);
                key = rawName;
            }
            // Prefixed cookies take precedence over plain cookies with the same name
            if (!cookies.containsKey(key) || cookie instanceof HostCookie || cookie instanceof SecureCookie) {
                cookies.put(key, cookie);
            }
        }
        return cookies;
    }


    /**
     * Populates the request with parsed cookies if not already set.
     *
     * @param request the incoming HTTP request to populate
     */
    protected void cookiesRequest(HttpRequest request) {
        if (request.getCookies() == null) {
            request.setCookies(parseCookies(request));
        }
    }

    /**
     * Serialises response cookies into {@code Set-Cookie} headers.
     *
     * @param response the outgoing HTTP response to write headers to
     */
    protected void cookiesResponse(HttpResponse response) {
        Multimap<String, Cookie> cookieMap = response.getCookies();
        if (cookieMap != null) {
            cookieMap.values().forEach(cookie ->
                    response.getHeaders().put("Set-Cookie", cookie.toHttpString()));
        }
    }

    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, HttpResponse, NNREQ, NNRES> next) {
        cookiesRequest(request);
        HttpResponse response = castToHttpResponse(next.next(request));
        if (response != null) {
            cookiesResponse(response);
        }

        return response;
    }
}
