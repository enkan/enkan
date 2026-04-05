package enkan.web.signature;

import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.sf.SfParameters;
import enkan.web.util.sf.SfValue;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureBaseBuilderTest {

    // ----------------------------------------------------------- derived components

    @Test
    void methodComponent() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@method"));
        assertThat(value).isEqualTo("GET");
    }

    @Test
    void pathComponent() {
        HttpRequest req = buildRequest("GET", "/foo/bar", null, "http", "example.com", 80);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@path"));
        assertThat(value).isEqualTo("/foo/bar");
    }

    @Test
    void queryComponentWithQueryString() {
        HttpRequest req = buildRequest("GET", "/", "a=1&b=2", "http", "example.com", 80);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@query"));
        assertThat(value).isEqualTo("?a=1&b=2");
    }

    @Test
    void queryComponentWithoutQueryString() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@query"));
        assertThat(value).isEqualTo("?");
    }

    @Test
    void schemeComponent() {
        HttpRequest req = buildRequest("GET", "/", null, "https", "example.com", 443);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@scheme"));
        assertThat(value).isEqualTo("https");
    }

    @Test
    void authorityComponentDefaultPort() {
        HttpRequest req = buildRequest("GET", "/", null, "https", "example.com", 443);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@authority"));
        assertThat(value).isEqualTo("example.com");
    }

    @Test
    void authorityComponentNonDefaultPort() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 8080);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@authority"));
        assertThat(value).isEqualTo("example.com:8080");
    }

    @Test
    void targetUriComponent() {
        HttpRequest req = buildRequest("GET", "/foo", "bar=baz", "https", "example.com", 443);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@target-uri"));
        assertThat(value).isEqualTo("https://example.com/foo?bar=baz");
    }

    @Test
    void requestTargetComponent() {
        HttpRequest req = buildRequest("GET", "/foo", "bar=baz", "https", "example.com", 443);
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@request-target"));
        assertThat(value).isEqualTo("/foo?bar=baz");
    }

    @Test
    void queryParamComponent() {
        HttpRequest req = buildRequest("GET", "/", "a=1&b=hello%20world", "http", "example.com", 80);
        Map<String, SfValue> params = new LinkedHashMap<>();
        params.put("name", new SfValue.SfString("b"));
        SignatureComponent comp = new SignatureComponent("@query-param", new SfParameters(params));
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, comp);
        assertThat(value).isEqualTo("hello world");
    }

    @Test
    void statusComponent() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        HttpResponse res = builder(HttpResponse.of("ok"))
                .set(HttpResponse::setStatus, 200)
                .build();
        String value = SignatureBaseBuilder.resolveComponentValue(req, res, SignatureComponent.of("@status"));
        assertThat(value).isEqualTo("200");
    }

    @Test
    void statusComponentWithoutResponseThrows() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        assertThatThrownBy(() ->
                SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("@status")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------- header components

    @Test
    void headerComponent() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        req.getHeaders().put("content-type", "application/json");
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("content-type"));
        assertThat(value).isEqualTo("application/json");
    }

    @Test
    void headerComponentCaseInsensitive() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        req.getHeaders().put("Content-Type", "text/html");
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("content-type"));
        assertThat(value).isEqualTo("text/html");
    }

    @Test
    void headerComponentTrimsWhitespace() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        req.getHeaders().put("x-custom", "  value  ");
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, SignatureComponent.of("x-custom"));
        assertThat(value).isEqualTo("value");
    }

    // ----------------------------------------------------------- full signature base

    @Test
    void fullSignatureBase() {
        HttpRequest req = buildRequest("GET", "/foo", "bar=baz", "https", "example.com", 443);
        req.getHeaders().put("content-type", "application/json");

        List<SignatureComponent> components = List.of(
                SignatureComponent.of("@method"),
                SignatureComponent.of("@path"),
                SignatureComponent.of("content-type")
        );
        Map<String, SfValue> paramMap = new LinkedHashMap<>();
        paramMap.put("created", new SfValue.SfInteger(1618884473L));
        paramMap.put("keyid", new SfValue.SfString("test-key"));
        SfParameters params = new SfParameters(paramMap);

        String base = SignatureBaseBuilder.buildSignatureBase(req, components, params);

        assertThat(base).isEqualTo(
                "\"@method\": GET\n" +
                "\"@path\": /foo\n" +
                "\"content-type\": application/json\n" +
                "\"@signature-params\": (\"@method\" \"@path\" \"content-type\");created=1618884473;keyid=\"test-key\"");
    }

    @Test
    void signatureBaseHasNoTrailingNewline() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        String base = SignatureBaseBuilder.buildSignatureBase(req, List.of(SignatureComponent.of("@method")),
                SfParameters.EMPTY);
        assertThat(base).doesNotEndWith("\n");
    }

    // ----------------------------------------------------------- serializeSignatureParams

    @Test
    void serializeSignatureParamsBasic() {
        List<SignatureComponent> components = List.of(
                SignatureComponent.of("@method"),
                SignatureComponent.of("@authority")
        );
        Map<String, SfValue> paramMap = new LinkedHashMap<>();
        paramMap.put("keyid", new SfValue.SfString("key1"));
        SfParameters params = new SfParameters(paramMap);

        String result = SignatureBaseBuilder.serializeSignatureParams(components, params);
        assertThat(result).isEqualTo("(\"@method\" \"@authority\");keyid=\"key1\"");
    }

    // ----------------------------------------------------------- helpers

    private static HttpRequest buildRequest(String method, String path, String query,
                                            String scheme, String host, int port) {
        HttpRequest req = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, method)
                .set(HttpRequest::setUri, path)
                .set(HttpRequest::setQueryString, query)
                .set(HttpRequest::setScheme, scheme)
                .set(HttpRequest::setServerName, host)
                .set(HttpRequest::setServerPort, port)
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();
        return req;
    }
}
