package enkan.data;

import enkan.util.HttpDateFormat;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CookieTest {

    @Test
    void createFactorySetsNameAndValue() {
        Cookie cookie = Cookie.create("session", "abc123");
        assertThat(cookie.getName()).isEqualTo("session");
        assertThat(cookie.getValue()).isEqualTo("abc123");
    }

    @Test
    void toHttpStringWithOnlyNameAndValue() {
        Cookie cookie = Cookie.create("foo", "bar");
        assertThat(cookie.toHttpString()).isEqualTo("foo=bar");
    }

    @Test
    void toHttpStringWithDomainAndPath() {
        Cookie cookie = Cookie.create("foo", "bar");
        cookie.setDomain("example.com");
        cookie.setPath("/app");
        assertThat(cookie.toHttpString()).isEqualTo("foo=bar; domain=example.com; path=/app");
    }

    @Test
    void toHttpStringWithMaxAge() {
        Cookie cookie = Cookie.create("foo", "bar");
        cookie.setMaxAge(3600);
        assertThat(cookie.toHttpString()).isEqualTo("foo=bar; max-age=3600");
    }

    @Test
    void toHttpStringWithHttpOnlyAndSecure() {
        Cookie cookie = Cookie.create("foo", "bar");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        assertThat(cookie.toHttpString()).isEqualTo("foo=bar; httponly; secure");
    }

    @Test
    void toHttpStringWithSameSite() {
        Cookie cookie = Cookie.create("foo", "bar");
        cookie.setSameSite("Strict");
        assertThat(cookie.toHttpString()).isEqualTo("foo=bar; samesite=Strict");
    }

    @Test
    void toHttpStringWithExpiresDate() {
        Cookie cookie = Cookie.create("foo", "bar");
        Date expires = new Date(0); // epoch
        cookie.setExpires(expires);
        String expected = "foo=bar; expires=" + HttpDateFormat.RFC1123.format(expires);
        assertThat(cookie.toHttpString()).isEqualTo(expected);
    }

    @Test
    void toHttpStringWithAllAttributes() {
        Cookie cookie = Cookie.create("session", "xyz");
        cookie.setDomain("example.com");
        cookie.setPath("/");
        Date expires = new Date(1000000000000L);
        cookie.setExpires(expires);
        cookie.setMaxAge(7200);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite("Lax");

        String result = cookie.toHttpString();
        assertThat(result).startsWith("session=xyz");
        assertThat(result).contains("; domain=example.com");
        assertThat(result).contains("; path=/");
        assertThat(result).contains("; expires=" + HttpDateFormat.RFC1123.format(expires));
        assertThat(result).contains("; max-age=7200");
        assertThat(result).contains("; httponly");
        assertThat(result).contains("; secure");
        assertThat(result).contains("; samesite=Lax");
    }

    @Test
    void createRejectsNullName() {
        assertThatThrownBy(() -> Cookie.create(null, "v"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsEmptyName() {
        assertThatThrownBy(() -> Cookie.create("", "v"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsNameWithEquals() {
        assertThatThrownBy(() -> Cookie.create("a=b", "v"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsNameWithSemicolon() {
        assertThatThrownBy(() -> Cookie.create("a;b", "v"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsNameWithSpace() {
        assertThatThrownBy(() -> Cookie.create("a b", "v"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAllowsNullValue() {
        Cookie cookie = Cookie.create("foo", null);
        assertThat(cookie.toHttpString()).isEqualTo("foo=");
    }

    @Test
    void createRejectsValueWithCrLf() {
        assertThatThrownBy(() -> Cookie.create("foo", "bar\r\nbaz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsValueWithSemicolon() {
        assertThatThrownBy(() -> Cookie.create("foo", "bar;baz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsValueWithBackslash() {
        assertThatThrownBy(() -> Cookie.create("foo", "bar\\baz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsValueWithDoubleQuote() {
        assertThatThrownBy(() -> Cookie.create("foo", "bar\"baz"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
