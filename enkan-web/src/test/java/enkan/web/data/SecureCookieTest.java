package enkan.web.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureCookieTest {

    @Test
    void createRejectsDoublePrefix() {
        assertThatThrownBy(() -> SecureCookie.create("__Secure-sid", "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toHttpStringPrependsSecurePrefix() {
        SecureCookie cookie = SecureCookie.create("sid", "xyz789");
        assertThat(cookie.toHttpString())
                .isEqualTo("__Secure-sid=xyz789; secure");
    }

    @Test
    void secureIsAlwaysTrue() {
        SecureCookie cookie = SecureCookie.create("sid", "abc");
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void setSecureFalseThrows() {
        SecureCookie cookie = SecureCookie.create("sid", "abc");
        assertThatThrownBy(() -> cookie.setSecure(false))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setSecureTrueIsNoOp() {
        SecureCookie cookie = SecureCookie.create("sid", "abc");
        cookie.setSecure(true); // should not throw
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void domainCanBeSet() {
        SecureCookie cookie = SecureCookie.create("sid", "abc");
        cookie.setDomain("example.com");
        assertThat(cookie.toHttpString())
                .isEqualTo("__Secure-sid=abc; domain=example.com; secure");
    }

    @Test
    void pathCanBeSet() {
        SecureCookie cookie = SecureCookie.create("sid", "abc");
        cookie.setPath("/app");
        assertThat(cookie.toHttpString())
                .isEqualTo("__Secure-sid=abc; path=/app; secure");
    }

    @Test
    void allAttributesArePreserved() {
        SecureCookie cookie = SecureCookie.create("sid", "abc");
        cookie.setDomain("example.com");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSameSite("Lax");
        cookie.setMaxAge(3600);

        String result = cookie.toHttpString();
        assertThat(result).startsWith("__Secure-sid=abc");
        assertThat(result).contains("; domain=example.com");
        assertThat(result).contains("; path=/");
        assertThat(result).contains("; max-age=3600");
        assertThat(result).contains("; httponly");
        assertThat(result).contains("; secure");
        assertThat(result).contains("; samesite=Lax");
    }
}
