package enkan.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostCookieTest {

    @Test
    void toHttpStringPrependsHostPrefix() {
        HostCookie cookie = HostCookie.create("token", "abc123");
        assertThat(cookie.toHttpString())
                .isEqualTo("__Host-token=abc123; path=/; secure");
    }

    @Test
    void secureIsAlwaysTrue() {
        HostCookie cookie = HostCookie.create("token", "abc");
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void pathIsAlwaysRoot() {
        HostCookie cookie = HostCookie.create("token", "abc");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void domainIsAlwaysNull() {
        HostCookie cookie = HostCookie.create("token", "abc");
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void setDomainThrows() {
        HostCookie cookie = HostCookie.create("token", "abc");
        assertThatThrownBy(() -> cookie.setDomain("example.com"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setDomainNullIsNoOp() {
        HostCookie cookie = HostCookie.create("token", "abc");
        cookie.setDomain(null); // should not throw
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void setSecureFalseThrows() {
        HostCookie cookie = HostCookie.create("token", "abc");
        assertThatThrownBy(() -> cookie.setSecure(false))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setSecureTrueIsNoOp() {
        HostCookie cookie = HostCookie.create("token", "abc");
        cookie.setSecure(true); // should not throw
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void setPathNonRootThrows() {
        HostCookie cookie = HostCookie.create("token", "abc");
        assertThatThrownBy(() -> cookie.setPath("/app"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setPathRootIsNoOp() {
        HostCookie cookie = HostCookie.create("token", "abc");
        cookie.setPath("/"); // should not throw
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void optionalAttributesArePreserved() {
        HostCookie cookie = HostCookie.create("token", "abc");
        cookie.setHttpOnly(true);
        cookie.setSameSite("Strict");
        assertThat(cookie.toHttpString())
                .isEqualTo("__Host-token=abc; path=/; httponly; secure; samesite=Strict");
    }
}
