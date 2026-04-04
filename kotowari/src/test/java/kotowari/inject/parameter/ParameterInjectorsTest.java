package kotowari.inject.parameter;

import enkan.collection.Parameters;
import enkan.data.*;
import enkan.web.data.ContentNegotiable;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.util.MixinUtils;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Locale;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class ParameterInjectorsTest {

    // --- HttpRequestInjector ---

    @Test
    void httpRequestInjectorIsApplicable() {
        HttpRequestInjector injector = new HttpRequestInjector();
        assertThat(injector.isApplicable(HttpRequest.class)).isTrue();
    }

    @Test
    void httpRequestInjectorReturnsRequestItself() {
        HttpRequestInjector injector = new HttpRequestInjector();
        HttpRequest request = new DefaultHttpRequest();
        assertThat(injector.getInjectObject(request)).isSameAs(request);
    }

    // --- SessionInjector ---

    @Test
    void sessionInjectorIsApplicable() {
        SessionInjector injector = new SessionInjector();
        assertThat(injector.isApplicable(Session.class)).isTrue();
        assertThat(injector.isApplicable(String.class)).isFalse();
    }

    @Test
    void sessionInjectorReturnsSessionFromRequest() {
        SessionInjector injector = new SessionInjector();
        Session session = new Session();
        session.put("key", "value");
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setSession, session)
                .build();
        assertThat(injector.getInjectObject(request)).isSameAs(session);
    }

    // --- FlashInjector ---

    @Test
    void flashInjectorIsApplicable() {
        FlashInjector<?> injector = new FlashInjector<>();
        assertThat(injector.isApplicable(Flash.class)).isTrue();
        assertThat(injector.isApplicable(String.class)).isFalse();
    }

    @Test
    void flashInjectorReturnsFlashFromRequest() {
        FlashInjector<?> injector = new FlashInjector<>();
        Flash<String> flash = new Flash<>("hello");
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setFlash, flash)
                .build();
        assertThat(injector.getInjectObject(request)).isSameAs(flash);
    }

    // --- PrincipalInjector ---

    @Test
    void principalInjectorIsApplicable() {
        PrincipalInjector injector = new PrincipalInjector();
        assertThat(injector.isApplicable(Principal.class)).isTrue();
        assertThat(injector.isApplicable(String.class)).isFalse();
    }

    @Test
    void principalInjectorReturnsPrincipalFromRequest() {
        PrincipalInjector injector = new PrincipalInjector();
        Principal principal = () -> "testuser";
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setPrincipal, principal)
                .build();
        assertThat(injector.getInjectObject(request)).isSameAs(principal);
    }

    @Test
    void principalInjectorReturnsNullForNullRequest() {
        PrincipalInjector injector = new PrincipalInjector();
        assertThat(injector.getInjectObject(null)).isNull();
    }

    // --- LocaleInjector ---

    @Test
    void localeInjectorIsApplicable() {
        LocaleInjector injector = new LocaleInjector();
        assertThat(injector.isApplicable(Locale.class)).isTrue();
        assertThat(injector.isApplicable(String.class)).isFalse();
    }

    @Test
    void localeInjectorReturnsLocaleFromContentNegotiableRequest() {
        LocaleInjector injector = new LocaleInjector();
        HttpRequest request = MixinUtils.mixin(new DefaultHttpRequest(), ContentNegotiable.class);
        ((ContentNegotiable) request).setLocale(Locale.JAPAN);
        assertThat(injector.getInjectObject(request)).isEqualTo(Locale.JAPAN);
    }

    @Test
    void localeInjectorReturnsNullForPlainRequest() {
        LocaleInjector injector = new LocaleInjector();
        HttpRequest request = new DefaultHttpRequest();
        assertThat(injector.getInjectObject(request)).isNull();
    }

    // --- ParametersInjector ---

    @Test
    void parametersInjectorIsApplicable() {
        ParametersInjector injector = new ParametersInjector();
        assertThat(injector.isApplicable(Parameters.class)).isTrue();
        assertThat(injector.isApplicable(String.class)).isFalse();
    }

    @Test
    void parametersInjectorReturnsParamsFromRequest() {
        ParametersInjector injector = new ParametersInjector();
        Parameters params = Parameters.of("q", "enkan");
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setParams, params)
                .build();
        assertThat(injector.getInjectObject(request)).isSameAs(params);
    }
}
