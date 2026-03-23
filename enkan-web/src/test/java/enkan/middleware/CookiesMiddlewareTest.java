package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.data.Cookie;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static enkan.util.BeanBuilder.builder;
import static enkan.util.ThreadingUtils.some;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class CookiesMiddlewareTest {
    private CookiesMiddleware middleware;
    private HttpRequest request;

    @BeforeEach
    void setup() {
        middleware = new CookiesMiddleware();
        request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setHeaders,
                        Headers.of("Host", "example.com"))
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setUri, "/prefix/")
                .set(HttpRequest::setQueryString, "a=b&c=d")
                .build();
    }

    @Test
    void cookieExpiresUsesGmtNotOffset() {
        Cookie cookie = Cookie.create("session", "abc");
        cookie.setExpires(new Date(0L)); // 1970-01-01T00:00:00Z
        String header = cookie.toHttpString();
        // RFC 6265 §4.1.2.1 requires GMT, not +0000
        assertThat(header).contains("GMT");
        assertThat(header).doesNotContain("+0000");
    }

    @Test
    void parse() {
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    assertThat(some(req.getCookies().get("A"), Cookie::getValue).orElseThrow(AssertionError::new))
                            .isEqualTo("hello");
                    assertThat(some(req.getCookies().get("B"), Cookie::getValue).orElseThrow(AssertionError::new))
                            .isEqualTo("1");

                    return builder(HttpResponse.of("hello"))
                            .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html"))
                            .build();
                });
        request.getHeaders().put("Cookie", "A=hello; B=1");
        middleware.handle(request, chain);
    }

    @Test
    void parsesRfcValidCookieOctets() {
        // RFC 6265 §4.1.1: spot-check one char from each allowed range using raw octets.
        // %x21=!, %x23=#, %x2D=-, %x3A=:, %x3C=<, %x5B=[, %x5D=], %x7E=~
        // Chars that have special meaning for form-decoding (+ as space, % as escape) are omitted.
        final String expectedValue = "!#-:<[]~";
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    Cookie cookie = req.getCookies().get("A");
                    assertThat(cookie).isNotNull();
                    assertThat(cookie.getValue()).isEqualTo(expectedValue);
                    return HttpResponse.of("ok");
                });
        request.getHeaders().put("Cookie", "A=!#-:<[]~");
        middleware.handle(request, chain);
    }

    @Test
    void backslashIsNotConsumedAsCookieOctet() {
        // Backslash (%x5C) is not a valid cookie-octet (RFC 6265 §4.1.1).
        // RE_COOKIE requires the value to be followed by a delimiter or end-of-input,
        // so BAD=\ (a trailing backslash) does not match and the cookie is not parsed at all.
        MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req -> {
                    assertThat(req.getCookies()).doesNotContainKey("BAD");
                    return HttpResponse.of("ok");
                });
        request.getHeaders().put("Cookie", "BAD=\\");
        middleware.handle(request, chain);
    }
}
