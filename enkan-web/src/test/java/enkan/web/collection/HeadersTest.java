package enkan.web.collection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HeadersTest {

    @Test
    void ofFactoryCreatesHeadersCorrectly() {
        Headers headers = Headers.of("content-type", "text/html");
        assertThat(headers.get("content-type")).isEqualTo("text/html");
    }

    @Test
    void ofFactoryWithMultipleEntries() {
        Headers headers = Headers.of("content-type", "text/html",
                "accept", "application/json");
        assertThat(headers.get("content-type")).isEqualTo("text/html");
        assertThat(headers.get("accept")).isEqualTo("application/json");
    }

    @Test
    void putAndGetAreCaseInsensitive() {
        Headers headers = Headers.empty();
        headers.put("Content-Type", "text/html");
        assertThat(headers.get("content-type")).isEqualTo("text/html");
        assertThat(headers.get("CONTENT-TYPE")).isEqualTo("text/html");
        assertThat(headers.get("Content-Type")).isEqualTo("text/html");
    }

    @Test
    void keySetReturnsCapitalizedNames() {
        Headers headers = Headers.of("content-type", "text/html",
                "accept-encoding", "gzip");
        Set<String> keys = headers.keySet();
        assertThat(keys).contains("Content-Type", "Accept-Encoding");
    }

    @Test
    void forEachHeaderIteratesWithCapitalizedNames() {
        Headers headers = Headers.of("content-type", "text/html",
                "accept", "application/json");
        List<String> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        headers.forEachHeader((name, value) -> {
            names.add(name);
            values.add(value);
        });
        assertThat(names).contains("Content-Type", "Accept");
        assertThat(values).contains("text/html", "application/json");
    }

    @Test
    void forEachHeaderHandlesMultiValueHeaders() {
        Headers headers = Headers.empty();
        headers.put("accept", List.of("text/html", "application/json"));
        List<String> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        headers.forEachHeader((name, value) -> {
            names.add(name);
            values.add(value);
        });
        assertThat(names).containsExactly("Accept", "Accept");
        assertThat(values).containsExactly("text/html", "application/json");
    }

    @Test
    void keySetCacheIsInvalidatedAfterPut() {
        Headers headers = Headers.of("content-type", "text/html");
        Set<String> keysBefore = headers.keySet();
        assertThat(keysBefore).containsExactly("Content-Type");

        headers.put("accept", "application/json");
        Set<String> keysAfter = headers.keySet();
        assertThat(keysAfter).contains("Content-Type", "Accept");
    }

    @Test
    void keySetCacheIsInvalidatedAfterRemove() {
        Headers headers = Headers.of("content-type", "text/html",
                "accept", "application/json");
        Set<String> keysBefore = headers.keySet();
        assertThat(keysBefore).contains("Content-Type", "Accept");

        headers.remove("accept");
        Set<String> keysAfter = headers.keySet();
        assertThat(keysAfter).containsExactly("Content-Type");
        assertThat(keysAfter).doesNotContain("Accept");
    }

    @Test
    void specialKeywordCapitalization() {
        Headers headers = Headers.empty();
        headers.put("content-type", "text/html");
        headers.put("x-csrf-token", "abc");
        headers.put("content-security-policy", "default-src 'self'");

        Set<String> keys = headers.keySet();
        assertThat(keys).contains("Content-Type", "X-Csrf-Token");
    }

    @Test
    void keywordsCspHttpSslAreAllUpperCase() {
        Headers headers = Headers.empty();
        headers.put("x-csp-header", "value1");
        headers.put("x-http-forwarded", "value2");
        headers.put("x-ssl-client", "value3");

        Set<String> keys = headers.keySet();
        assertThat(keys).contains("X-CSP-Header", "X-HTTP-Forwarded", "X-SSL-Client");
    }
}
