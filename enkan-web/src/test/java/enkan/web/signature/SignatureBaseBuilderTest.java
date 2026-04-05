package enkan.web.signature;

import enkan.exception.MisconfigurationException;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.util.sf.SfParameters;
import enkan.web.util.sf.SfValue;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static enkan.util.BeanBuilder.builder;
import static enkan.web.signature.SignatureTestSupport.buildRequest;
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
                .isInstanceOf(MisconfigurationException.class);
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

    // ----------------------------------------------------------- ;sf canonicalization

    @Test
    void sfCanonicalizationParsesAndReserializes() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        // Set a header with non-canonical SF (extra spaces)
        req.getHeaders().put("x-dict", "a=1,   b=2");
        Map<String, SfValue> params = new LinkedHashMap<>();
        params.put("sf", new SfValue.SfBoolean(true));
        SignatureComponent comp = new SignatureComponent("x-dict", new SfParameters(params));
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, comp);
        // Re-serialized should have normalized spacing
        assertThat(value).isEqualTo("a=1, b=2");
    }

    // ----------------------------------------------------------- ;key dictionary member

    @Test
    void keyParamExtractsDictionaryMember() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        req.getHeaders().put("x-dict", "a=1, b=2, c=3");
        Map<String, SfValue> params = new LinkedHashMap<>();
        params.put("key", new SfValue.SfString("b"));
        SignatureComponent comp = new SignatureComponent("x-dict", new SfParameters(params));
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, comp);
        assertThat(value).isEqualTo("2");
    }

    @Test
    void keyParamWithMissingMemberThrows() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        req.getHeaders().put("x-dict", "a=1");
        Map<String, SfValue> params = new LinkedHashMap<>();
        params.put("key", new SfValue.SfString("missing"));
        SignatureComponent comp = new SignatureComponent("x-dict", new SfParameters(params));
        assertThatThrownBy(() ->
                SignatureBaseBuilder.resolveComponentValue(req, null, comp))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------- @query-param edge cases

    @Test
    void queryParamEmptyValue() {
        HttpRequest req = buildRequest("GET", "/", "a=&b=2", "http", "example.com", 80);
        Map<String, SfValue> params = new LinkedHashMap<>();
        params.put("name", new SfValue.SfString("a"));
        SignatureComponent comp = new SignatureComponent("@query-param", new SfParameters(params));
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, comp);
        assertThat(value).isEmpty();
    }

    @Test
    void queryParamMissingThrows() {
        HttpRequest req = buildRequest("GET", "/", "a=1", "http", "example.com", 80);
        Map<String, SfValue> params = new LinkedHashMap<>();
        params.put("name", new SfValue.SfString("nonexistent"));
        SignatureComponent comp = new SignatureComponent("@query-param", new SfParameters(params));
        assertThatThrownBy(() ->
                SignatureBaseBuilder.resolveComponentValue(req, null, comp))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryParamWithoutNameParamThrows() {
        HttpRequest req = buildRequest("GET", "/", "a=1", "http", "example.com", 80);
        // @query-param with no ;name parameter is a developer misconfiguration
        SignatureComponent comp = new SignatureComponent("@query-param", SfParameters.EMPTY);
        assertThatThrownBy(() ->
                SignatureBaseBuilder.resolveComponentValue(req, null, comp))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    void queryParamMultipleValuesJoined() {
        // RFC 9421 §2.2.8: multiple occurrences of a parameter are joined with ", "
        HttpRequest req = buildRequest("GET", "/", "tag=a&id=1&tag=b&tag=c", "http", "example.com", 80);
        Map<String, SfValue> params = new LinkedHashMap<>();
        params.put("name", new SfValue.SfString("tag"));
        SignatureComponent comp = new SignatureComponent("@query-param", new SfParameters(params));
        String value = SignatureBaseBuilder.resolveComponentValue(req, null, comp);
        assertThat(value).isEqualTo("a, b, c");
    }

    // ----------------------------------------------------------- response header resolution

    @Test
    void responseHeaderResolvedWhenResponsePresent() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        HttpResponse res = builder(HttpResponse.of("ok"))
                .set(HttpResponse::setStatus, 200)
                .build();
        res.getHeaders().put("x-response-header", "resp-value");
        String value = SignatureBaseBuilder.resolveComponentValue(req, res, SignatureComponent.of("x-response-header"));
        assertThat(value).isEqualTo("resp-value");
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

}
