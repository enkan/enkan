package enkan.web.data;

import enkan.web.collection.Headers;
import enkan.collection.Parameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultHttpRequestTest {

    @Test
    void defaultStateHasNullFields() {
        DefaultHttpRequest request = new DefaultHttpRequest();
        assertThat(request.getUri()).isNull();
        assertThat(request.getRequestMethod()).isNull();
        assertThat(request.getHeaders()).isNull();
        assertThat(request.getParams()).isNull();
        assertThat(request.getBody()).isNull();
        assertThat(request.getQueryString()).isNull();
        assertThat(request.getScheme()).isNull();
        assertThat(request.getProtocol()).isNull();
        assertThat(request.getContentType()).isNull();
        assertThat(request.getContentLength()).isNull();
        assertThat(request.getCharacterEncoding()).isNull();
        assertThat(request.getSession()).isNull();
        assertThat(request.getCookies()).isNull();
        assertThat(request.getFormParams()).isNull();
        assertThat(request.getQueryParams()).isNull();
    }

    @Test
    void setRequestMethodNormalizesToUppercase() {
        DefaultHttpRequest request = new DefaultHttpRequest();
        request.setRequestMethod("get");
        assertThat(request.getRequestMethod()).isEqualTo("GET");

        request.setRequestMethod("post");
        assertThat(request.getRequestMethod()).isEqualTo("POST");

        request.setRequestMethod("Put");
        assertThat(request.getRequestMethod()).isEqualTo("PUT");
    }

    @Test
    void setRequestMethodNullStaysNull() {
        DefaultHttpRequest request = new DefaultHttpRequest();
        request.setRequestMethod(null);
        assertThat(request.getRequestMethod()).isNull();
    }

    @Test
    void extensionsSetAndGet() {
        DefaultHttpRequest request = new DefaultHttpRequest();
        request.setExtension("myKey", "myValue");
        String value = request.getExtension("myKey");
        assertThat(value).isEqualTo("myValue");
    }

    @Test
    void extensionsGetNonExistentReturnsNull() {
        DefaultHttpRequest request = new DefaultHttpRequest();
        Object value = request.getExtension("nonExistent");
        assertThat(value).isNull();
    }

    @Test
    void uriGetterAndSetter() {
        DefaultHttpRequest request = new DefaultHttpRequest();
        request.setUri("/api/users");
        assertThat(request.getUri()).isEqualTo("/api/users");
    }

    @Test
    void headersGetterAndSetter() {
        DefaultHttpRequest request = new DefaultHttpRequest();
        Headers headers = Headers.of("content-type", "text/html");
        request.setHeaders(headers);
        assertThat(request.getHeaders()).isSameAs(headers);
        assertThat(request.getHeaders().get("content-type")).isEqualTo("text/html");
    }

    @Test
    void paramsGetterAndSetter() {
        DefaultHttpRequest request = new DefaultHttpRequest();
        Parameters params = Parameters.of("key", "value");
        request.setParams(params);
        assertThat(request.getParams()).isSameAs(params);
        assertThat(request.getParams().get("key")).isEqualTo("value");
    }
}
