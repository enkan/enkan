package kotowari.inject.parameter;

import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.util.MixinUtils;
import kotowari.data.BodyDeserializable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BodySerializableInjectorTest {

    private final BodySerializableInjector<?> injector = new BodySerializableInjector<>();

    private HttpRequest requestWithBody(Object body) {
        HttpRequest request = MixinUtils.mixin(new DefaultHttpRequest(), BodyDeserializable.class);
        ((BodyDeserializable) request).setDeserializedBody(body);
        return request;
    }

    @Test
    void notApplicableByTypeAlone() {
        assertThat(injector.isApplicable(String.class)).isFalse();
        assertThat(injector.isApplicable(Object.class)).isFalse();
    }

    @Test
    void notApplicableWhenRequestIsNull() {
        assertThat(injector.isApplicable(String.class, null)).isFalse();
    }

    @Test
    void notApplicableWhenRequestIsNotBodyDeserializable() {
        HttpRequest request = new DefaultHttpRequest();
        assertThat(injector.isApplicable(String.class, request)).isFalse();
    }

    @Test
    void notApplicableWhenBodyIsNull() {
        HttpRequest request = requestWithBody(null);
        assertThat(injector.isApplicable(String.class, request)).isFalse();
    }

    @Test
    void applicableWhenBodyTypeMatches() {
        HttpRequest request = requestWithBody("hello");
        assertThat(injector.isApplicable(String.class, request)).isTrue();
    }

    @Test
    void notApplicableWhenBodyTypeMismatches() {
        HttpRequest request = requestWithBody("hello");
        assertThat(injector.isApplicable(Integer.class, request)).isFalse();
    }
}
