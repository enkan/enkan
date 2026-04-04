package enkan.web.util;

import enkan.web.collection.Headers;
import enkan.web.data.HttpResponse;
import org.junit.jupiter.api.Test;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class HttpResponseUtilsTest {
    @Test
    void charset() {
        HttpResponse response = builder(HttpResponse.of("for testing"))
                .set(HttpResponse::setHeaders, Headers.of("Content-Type", "text/html"))
                .build();
        HttpResponseUtils.charset(response, "ISO_8859_1");
        assertThat(response.getHeaders().get("Content-Type"))
                .isEqualTo("text/html; charset=ISO_8859_1");
    }

    @Test
    void charsetWithoutContentType() {
        HttpResponse response = HttpResponse.of("for testing");
        response.getHeaders().remove("Content-Type");
        HttpResponseUtils.charset(response, "ISO_8859_1");
        assertThat(response.getHeaders().get("Content-Type"))
                .isEqualTo("text/plain; charset=ISO_8859_1");
    }
}
