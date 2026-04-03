package enkan.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.predicate.AnyPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForwardedMiddlewareTest {

    private ForwardedMiddleware middleware;
    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain;

    @BeforeEach
    void setup() {
        middleware = new ForwardedMiddleware();
        // Default trustedProxies: 127.0.0.0/8 and ::1/128
        chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("ok")).build());
    }

    @Test
    void parsesRfc7239ForwardedHeaderFromTrustedProxy() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=192.0.2.1;proto=https;host=example.com"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("192.0.2.1");
        assertThat(request.getScheme()).isEqualTo("https");
        assertThat(request.getServerName()).isEqualTo("example.com");
    }

    @Test
    void takesLeftmostHopFromMultipleForwardedEntries() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=192.0.2.1;proto=https, for=198.51.100.17;proto=http"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("192.0.2.1");
        assertThat(request.getScheme()).isEqualTo("https");
    }

    @Test
    void appliesLegacyXForwardedHeadersFromTrustedProxy() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setServerName, "internal.host")
                .set(HttpRequest::setHeaders, Headers.of(
                        "X-Forwarded-For", "203.0.113.5, 198.51.100.1",
                        "X-Forwarded-Proto", "https",
                        "X-Forwarded-Host", "example.com"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("203.0.113.5");
        assertThat(request.getScheme()).isEqualTo("https");
        assertThat(request.getServerName()).isEqualTo("example.com");
    }

    @Test
    void prefersStandardOverLegacyWhenPreferStandardIsTrue() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of(
                        "Forwarded", "for=192.0.2.1;proto=https;host=standard.example.com",
                        "X-Forwarded-For", "10.0.0.1",
                        "X-Forwarded-Proto", "http",
                        "X-Forwarded-Host", "legacy.example.com"))
                .build();

        middleware.setPreferStandard(true);
        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("192.0.2.1");
        assertThat(request.getScheme()).isEqualTo("https");
        assertThat(request.getServerName()).isEqualTo("standard.example.com");
    }

    @Test
    void prefersLegacyOverStandardWhenPreferStandardIsFalse() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "https")
                .set(HttpRequest::setHeaders, Headers.of(
                        "Forwarded", "for=192.0.2.1;proto=https;host=standard.example.com",
                        "X-Forwarded-For", "10.0.0.1",
                        "X-Forwarded-Proto", "http",
                        "X-Forwarded-Host", "legacy.example.com"))
                .build();

        middleware.setPreferStandard(false);
        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("10.0.0.1");
        assertThat(request.getScheme()).isEqualTo("http");
        assertThat(request.getServerName()).isEqualTo("legacy.example.com");
    }

    @Test
    void ignoresHeadersFromUntrustedProxy() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "203.0.113.99")  // not in trusted list
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setServerName, "internal.host")
                .set(HttpRequest::setHeaders, Headers.of(
                        "X-Forwarded-For", "1.2.3.4",
                        "X-Forwarded-Proto", "https",
                        "X-Forwarded-Host", "attacker.example.com"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("203.0.113.99");
        assertThat(request.getScheme()).isEqualTo("http");
        assertThat(request.getServerName()).isEqualTo("internal.host");
    }

    @Test
    void ignoresInvalidSchemeInXForwardedProto() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of(
                        "X-Forwarded-Proto", "ftp"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getScheme()).isEqualTo("http");
    }

    @Test
    void stripsQuotesFromRfc7239ForValue() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=\"192.0.2.1\";proto=https"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("192.0.2.1");
    }

    @Test
    void trustsCustomCidrRange() {
        middleware.setTrustedProxies(List.of("10.0.0.0/8"));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "10.1.2.3")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("X-Forwarded-Proto", "https"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getScheme()).isEqualTo("https");
    }

    @Test
    void stripsPortFromRfc7239IPv4ForValue() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=192.0.2.1:51348;proto=https"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("192.0.2.1");
    }

    @Test
    void stripsPortFromRfc7239IPv6ForValue() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=\"[2001:db8::1]:51348\";proto=https"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("2001:db8::1");
    }

    @Test
    void doesNotThrowWhenRemoteAddrIsNull() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("X-Forwarded-Proto", "https"))
                .build();
        // remoteAddr is null — should pass through unchanged without throwing

        middleware.handle(request, chain);

        assertThat(request.getScheme()).isEqualTo("http");
    }

    @Test
    void trustAllCidrMatchesAnyAddress() {
        middleware.setTrustedProxies(List.of("0.0.0.0/0"));

        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "203.0.113.99")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("X-Forwarded-Proto", "https"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getScheme()).isEqualTo("https");
    }

    @Test
    void doesNotSetRemoteAddrForUnknownForValue() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=unknown;proto=https"))
                .build();

        middleware.handle(request, chain);

        // remoteAddr should remain the proxy IP, not "unknown"
        assertThat(request.getRemoteAddr()).isEqualTo("127.0.0.1");
        // proto is still applied
        assertThat(request.getScheme()).isEqualTo("https");
    }

    @Test
    void doesNotSetRemoteAddrForObfuscatedForValue() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=_hidden;proto=https"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("127.0.0.1");
    }

    @Test
    void rejectsOutOfRangePrefixLength() {
        assertThatThrownBy(() -> middleware.setTrustedProxies(List.of("10.0.0.0/33")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void rejectsNegativePrefixLength() {
        assertThatThrownBy(() -> middleware.setTrustedProxies(List.of("10.0.0.0/-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyCidrAddress() {
        assertThatThrownBy(() -> middleware.setTrustedProxies(List.of("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsCidrWithSlashOnlyAddress() {
        // "/8" has an empty address part — must not silently resolve to loopback
        assertThatThrownBy(() -> middleware.setTrustedProxies(List.of("/8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsHostnameInCidr() {
        assertThatThrownBy(() -> middleware.setTrustedProxies(List.of("localhost/32")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void rejectsIpv4MappedIpv6CidrWithOversizedPrefix() {
        // Java normalises ::ffff:127.0.0.0 to Inet4Address, so /104 exceeds the IPv4 max of /32
        assertThatThrownBy(() -> middleware.setTrustedProxies(List.of("::ffff:127.0.0.0/104")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void handlesMultiValueForwardedHeader() {
        // Multiple Forwarded headers stored as a List in Headers — must join with ", " not toString()
        Headers headers = Headers.empty();
        headers.put("Forwarded", "for=192.0.2.1;proto=https");
        headers.put("Forwarded", "for=198.51.100.17;proto=http");
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, headers)
                .build();

        middleware.handle(request, chain);

        // leftmost (original client) must be used, not "[for=..., for=...]"
        assertThat(request.getRemoteAddr()).isEqualTo("192.0.2.1");
        assertThat(request.getScheme()).isEqualTo("https");
    }

    @Test
    void ignoresCommaInsideQuotedForwardedValue() {
        // A quoted host value containing a comma must not be treated as a hop separator
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=192.0.2.1;host=\"example.com,alt\";proto=https"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getRemoteAddr()).isEqualTo("192.0.2.1");
        assertThat(request.getScheme()).isEqualTo("https");
        assertThat(request.getServerName()).isEqualTo("example.com,alt");
    }

    @Test
    void ignoresMalformedIpv6ForValueWithMissingClosingBracket() {
        // "[::1" without closing ']' must not throw StringIndexOutOfBoundsException
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=\"[::1\";proto=https"))
                .build();

        middleware.handle(request, chain);

        // malformed for= is skipped; remoteAddr unchanged, proto still applied
        assertThat(request.getRemoteAddr()).isEqualTo("127.0.0.1");
        assertThat(request.getScheme()).isEqualTo("https");
    }

    @Test
    void doesNotSetRemoteAddrForEmptyForValue() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("Forwarded", "for=\"\";proto=https"))
                .build();

        middleware.handle(request, chain);

        // for="" is an empty value — remoteAddr must not be overwritten with ""
        assertThat(request.getRemoteAddr()).isEqualTo("127.0.0.1");
        assertThat(request.getScheme()).isEqualTo("https");
    }

    @Test
    void rejectsHexOnlyHostnameInCidr() {
        // "cafe" passes old isNumericIp but must be rejected — no '.' or ':'
        assertThatThrownBy(() -> middleware.setTrustedProxies(List.of("cafe/32")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void ipv4MappedRemoteAddrMatchesIpv4Cidr() {
        // Servlet containers on dual-stack servers may provide remoteAddr as ::ffff:x.x.x.x.
        // normalizeToIpv4IfMapped must be applied so it still matches an IPv4 CIDR.
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRemoteAddr, "::ffff:127.0.0.1")
                .set(HttpRequest::setScheme, "http")
                .set(HttpRequest::setHeaders, Headers.of("X-Forwarded-Proto", "https"))
                .build();

        middleware.handle(request, chain);

        assertThat(request.getScheme()).isEqualTo("https");
    }
}
