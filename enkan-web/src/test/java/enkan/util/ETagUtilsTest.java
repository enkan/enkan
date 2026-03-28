package enkan.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ETagUtilsTest {

    @Test
    void generateWeakETagFromString() {
        String etag = ETagUtils.generateWeakETag("hello", null);
        assertThat(etag).isNotNull();
        assertThat(etag).startsWith("W/\"");
        assertThat(etag).endsWith("\"");
    }

    @Test
    void generateWeakETagFromBytes() {
        String etag = ETagUtils.generateWeakETag(new byte[]{1, 2, 3}, null);
        assertThat(etag).isNotNull().startsWith("W/\"");
    }

    @Test
    void generateWeakETagReturnsNullForInputStream() {
        assertThat(ETagUtils.generateWeakETag(new ByteArrayInputStream(new byte[0]), null))
                .isNull();
    }

    @Test
    void generateWeakETagReturnsNullForNull() {
        assertThat(ETagUtils.generateWeakETag(null, null)).isNull();
    }

    @Test
    void contentEncodingAffectsETag() {
        String plain = ETagUtils.generateWeakETag("hello", null);
        String gzipped = ETagUtils.generateWeakETag("hello", "gzip");
        assertThat(plain).isNotEqualTo(gzipped);
    }

    @Test
    void sameContentProducesSameETag() {
        String a = ETagUtils.generateWeakETag("hello", null);
        String b = ETagUtils.generateWeakETag("hello", null);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void strongMatchBothStrong() {
        assertThat(ETagUtils.strongMatch("\"1\"", "\"1\"")).isTrue();
    }

    @Test
    void strongMatchBothWeak() {
        assertThat(ETagUtils.strongMatch("W/\"1\"", "W/\"1\"")).isFalse();
    }

    @Test
    void strongMatchOneWeak() {
        assertThat(ETagUtils.strongMatch("W/\"1\"", "\"1\"")).isFalse();
    }

    @Test
    void strongMatchDifferent() {
        assertThat(ETagUtils.strongMatch("\"1\"", "\"2\"")).isFalse();
    }

    @Test
    void strongMatchNull() {
        assertThat(ETagUtils.strongMatch(null, "\"1\"")).isFalse();
        assertThat(ETagUtils.strongMatch("\"1\"", null)).isFalse();
    }

    @Test
    void weakMatchBothWeak() {
        assertThat(ETagUtils.weakMatch("W/\"1\"", "W/\"1\"")).isTrue();
    }

    @Test
    void weakMatchOneWeak() {
        assertThat(ETagUtils.weakMatch("W/\"1\"", "\"1\"")).isTrue();
    }

    @Test
    void weakMatchBothStrong() {
        assertThat(ETagUtils.weakMatch("\"1\"", "\"1\"")).isTrue();
    }

    @Test
    void weakMatchDifferent() {
        assertThat(ETagUtils.weakMatch("\"1\"", "\"2\"")).isFalse();
    }

    @Test
    void weakMatchNull() {
        assertThat(ETagUtils.weakMatch(null, "\"1\"")).isFalse();
    }

    @Test
    void matchesHeaderWildcard() {
        assertThat(ETagUtils.matchesHeader("*", "\"foo\"", true)).isTrue();
        assertThat(ETagUtils.matchesHeader("*", "\"foo\"", false)).isTrue();
    }

    @Test
    void matchesHeaderListWeak() {
        assertThat(ETagUtils.matchesHeader("\"a\", \"b\", \"c\"", "\"b\"", true)).isTrue();
        assertThat(ETagUtils.matchesHeader("\"a\", \"b\"", "\"c\"", true)).isFalse();
    }

    @Test
    void matchesHeaderListStrong() {
        assertThat(ETagUtils.matchesHeader("\"a\", \"b\"", "\"b\"", false)).isTrue();
        assertThat(ETagUtils.matchesHeader("W/\"a\", \"b\"", "W/\"a\"", false)).isFalse();
    }

    @Test
    void matchesHeaderNullInputs() {
        assertThat(ETagUtils.matchesHeader(null, "\"a\"", true)).isFalse();
        assertThat(ETagUtils.matchesHeader("\"a\"", null, true)).isFalse();
    }
}
