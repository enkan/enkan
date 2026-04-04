package enkan.web.middleware;

import enkan.collection.OptionMap;
import enkan.data.*;
import enkan.web.data.*;
import enkan.web.middleware.session.MemoryStore;
import enkan.util.MixinUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static enkan.util.BeanBuilder.builder;
import static enkan.util.ReflectionUtils.tryReflection;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class SessionMiddlewareTest {
    @Test
    void restoreSession() {
        SessionMiddleware middleware = new SessionMiddleware();
        HttpRequest request = builder(new DefaultHttpRequest())
                .build();
        request = MixinUtils.mixin(request, WebSessionAvailable.class);

        UUID sessionKey = UUID.randomUUID();
        Session session = new Session();
        session.put("name", "kawasima");

        tryReflection(() -> {
            Field storeField = SessionMiddleware.class.getDeclaredField("store");
            storeField.setAccessible(true);
            MemoryStore store = (MemoryStore) storeField.get(middleware);
            store.write(sessionKey.toString(), session);
            return store;
        });
        Map<String, Cookie> params = new HashMap<>();
        params.put("enkan-session", Cookie.create("enkan-session", sessionKey.toString()));
        request.setCookies(params);
        middleware.sessionRequest(request);
        assertThat(request.getSession().get("name"))
                .isEqualTo("kawasima");
    }

    @Test
    void sessionIdNotSent() {
        SessionMiddleware middleware = new SessionMiddleware();
        HttpRequest request = builder(new DefaultHttpRequest())
                .build();
        request = MixinUtils.mixin(request, WebSessionAvailable.class);

        UUID sessionKey = UUID.randomUUID();
        Session session = new Session();
        session.put("name", "kawasima");

        tryReflection(() -> {
            Field storeField = SessionMiddleware.class.getDeclaredField("store");
            storeField.setAccessible(true);
            MemoryStore store = (MemoryStore) storeField.get(middleware);
            store.write(sessionKey.toString(), session);
            return store;
        });
        middleware.sessionRequest(request);
        assertThat(request.getSession()).isNull();
    }

    @Test
    void populateAttrsAppliesConfiguredAttributes() {
        SessionMiddleware middleware = new SessionMiddleware();
        middleware.setCookieAttrs(OptionMap.of(
                "domain", "example.com",
                "path", "/app",
                "secure", true,
                "httpOnly", false));

        Cookie cookie = Cookie.create("enkan-session", "value");
        middleware.populateAttrs(cookie);

        assertThat(cookie.getDomain()).isEqualTo("example.com");
        assertThat(cookie.getPath()).isEqualTo("/app");
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void populateAttrsSkipsAbsentAttributes() {
        SessionMiddleware middleware = new SessionMiddleware();
        // Only "path" is set — "domain" and "secure" are absent.
        middleware.setCookieAttrs(OptionMap.of("path", "/"));

        Cookie cookie = Cookie.create("enkan-session", "value");
        middleware.populateAttrs(cookie);

        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void populateAttrsSetsSameSite() {
        SessionMiddleware middleware = new SessionMiddleware();
        middleware.setCookieAttrs(OptionMap.of(
                "httpOnly", true,
                "path", "/",
                "sameSite", "Strict"));

        Cookie cookie = Cookie.create("enkan-session", "value");
        middleware.populateAttrs(cookie);

        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void defaultCookieAttrsIncludeSameSiteLax() {
        SessionMiddleware middleware = new SessionMiddleware();

        Cookie cookie = Cookie.create("enkan-session", "value");
        middleware.populateAttrs(cookie);

        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
    }
}
