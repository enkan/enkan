package kotowari.inject.parameter;

import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.data.jpa.EntityManageable;
import enkan.util.MixinUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class EntityManagerInjectorTest {
    private EntityManagerInjector injector;

    @BeforeEach
    void setUp() {
        injector = new EntityManagerInjector();
    }

    @Test
    void isApplicableForEntityManagerType() {
        assertThat(injector.isApplicable(EntityManager.class)).isTrue();
    }

    @Test
    void isNotApplicableForOtherTypes() {
        assertThat(injector.isApplicable(String.class)).isFalse();
        assertThat(injector.isApplicable(Object.class)).isFalse();
    }

    @Test
    void getInjectObjectReturnsEntityManagerFromEntityManageableRequest() {
        HttpRequest request = MixinUtils.mixin(new DefaultHttpRequest(), EntityManageable.class);
        EntityManager em = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[]{ EntityManager.class },
                (proxy, method, args) -> null);
        ((EntityManageable) request).setEntityManager(em);

        assertThat(injector.getInjectObject(request)).isSameAs(em);
    }

    @Test
    void getInjectObjectReturnsNullWhenRequestIsNotEntityManageable() {
        HttpRequest request = new DefaultHttpRequest();
        assertThat(injector.getInjectObject(request)).isNull();
    }

    @Test
    void getInjectObjectReturnsNullForNullRequest() {
        assertThat(injector.getInjectObject(null)).isNull();
    }
}
