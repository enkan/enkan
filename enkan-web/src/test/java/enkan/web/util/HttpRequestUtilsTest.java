package enkan.web.util;

import enkan.util.MixinUtils;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.data.Traceable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class HttpRequestUtilsTest {
    @Test
    void testMixin() {
        HttpRequest request = new DefaultHttpRequest();
        request = MixinUtils.mixin(request, Traceable.class);
        assertThat(request).isNotNull();

        for (int i = 0; i < 1000000; i++) {
            request.setId("ABC123");
        }
        assertThat(request.getId()).isEqualTo("ABC123");
    }

    @Test
    void testMixin2() {
        class TraceableReq extends DefaultHttpRequest {
        }
        TraceableReq request = new TraceableReq();
        for (int i = 0; i < 1000000; i++) {
            request.setId("ABC123");
        }
        assertThat(request.getId()).isEqualTo("ABC123");
    }

    @Test
    void testMixin3() throws Exception {
        class TraceableReq extends DefaultHttpRequest {
        }
        TraceableReq request = new TraceableReq();
        Method m = TraceableReq.class.getMethod("setId", String.class);
        for (int i = 0; i < 1000000; i++) {
            m.invoke(request, "ABC123");
        }
        assertThat(request.getId()).isEqualTo("ABC123");
    }
}
