package enkan.web.http.fields.digest;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DigestFieldsTest {

    // ----------------------------------------------------------- computeDigestHeader

    @Test
    void sha256HeaderHasCorrectFormat() {
        String header = DigestFields.computeDigestHeader("hello".getBytes(), "sha-256");
        // SF Dictionary: sha-256=:base64value:
        assertThat(header).startsWith("sha-256=:");
        assertThat(header).endsWith(":");
    }

    @Test
    void sha512HeaderHasCorrectFormat() {
        String header = DigestFields.computeDigestHeader("hello".getBytes(), "sha-512");
        assertThat(header).startsWith("sha-512=:");
        assertThat(header).endsWith(":");
    }

    @Test
    void sha256OfEmptyBytesMatchesKnownValue() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        byte[] expected = hexToBytes("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        String header = DigestFields.computeDigestHeader(new byte[0], "sha-256");
        // Extract the base64 part between the colons
        String b64 = header.substring("sha-256=:".length(), header.length() - 1);
        byte[] actual = Base64.getDecoder().decode(b64);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void sha256OfHelloMatchesKnownValue() {
        // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        byte[] expected = hexToBytes("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        String header = DigestFields.computeDigestHeader("hello".getBytes(), "sha-256");
        String b64 = header.substring("sha-256=:".length(), header.length() - 1);
        byte[] actual = Base64.getDecoder().decode(b64);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void unsupportedAlgorithmThrows() {
        assertThatThrownBy(() -> DigestFields.computeDigestHeader("x".getBytes(), "md5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("md5");
    }

    @Test
    void sameInputProducesSameHeader() {
        String a = DigestFields.computeDigestHeader("test".getBytes(), "sha-256");
        String b = DigestFields.computeDigestHeader("test".getBytes(), "sha-256");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentInputsProduceDifferentHeaders() {
        String a = DigestFields.computeDigestHeader("foo".getBytes(), "sha-256");
        String b = DigestFields.computeDigestHeader("bar".getBytes(), "sha-256");
        assertThat(a).isNotEqualTo(b);
    }

    // ----------------------------------------------------------- negotiateAlgorithm

    @Test
    void nullHeaderReturnsDefault() {
        assertThat(DigestFields.negotiateAlgorithm(null, "sha-256")).isEqualTo("sha-256");
    }

    @Test
    void nullHeaderWithNullDefaultReturnsNull() {
        assertThat(DigestFields.negotiateAlgorithm(null, null)).isNull();
    }

    @Test
    void singleSupportedAlgorithmIsSelected() {
        // Client sends Want-Content-Digest: sha-256=5
        assertThat(DigestFields.negotiateAlgorithm("sha-256=5", "sha-512"))
                .isEqualTo("sha-256");
    }

    @Test
    void highestPriorityAlgorithmIsSelected() {
        // sha-512 has priority 10, sha-256 has priority 3 — pick sha-512
        assertThat(DigestFields.negotiateAlgorithm("sha-256=3, sha-512=10", "sha-256"))
                .isEqualTo("sha-512");
    }

    @Test
    void unsupportedAlgorithmOnlyReturnsNull() {
        // Client only wants md5 — we cannot satisfy, return null
        assertThat(DigestFields.negotiateAlgorithm("md5=5", "sha-256")).isNull();
    }

    @Test
    void mixedSupportedAndUnsupportedPicksSupported() {
        assertThat(DigestFields.negotiateAlgorithm("md5=10, sha-256=1", "sha-256"))
                .isEqualTo("sha-256");
    }

    @Test
    void zeroPreferenceIsExcluded() {
        // RFC 9530 §4: a preference value of 0 means the client does not want this field.
        // The algorithm must be treated as absent — return null, not the zero-priority algorithm.
        assertThat(DigestFields.negotiateAlgorithm("sha-256=0", "sha-512")).isNull();
    }

    @Test
    void zeroPreferenceWithOtherAlgorithmPicksNonZero() {
        // sha-256=0 is excluded; sha-512=3 is selected
        assertThat(DigestFields.negotiateAlgorithm("sha-256=0, sha-512=3", "sha-256"))
                .isEqualTo("sha-512");
    }

    @Test
    void malformedWantHeaderFallsBackToDefault() {
        // Malformed SF value — must not throw; fall back to defaultAlgorithm
        assertThat(DigestFields.negotiateAlgorithm("!!!not valid SF!!!", "sha-256"))
                .isEqualTo("sha-256");
    }

    @Test
    void malformedWantHeaderWithNullDefaultReturnsNull() {
        assertThat(DigestFields.negotiateAlgorithm("!!!not valid SF!!!", null))
                .isNull();
    }

    // ----------------------------------------------------------- toJcaAlgorithm

    @Test
    void sha256ToJca() {
        assertThat(DigestFields.toJcaAlgorithm("sha-256")).isEqualTo("SHA-256");
    }

    @Test
    void sha512ToJca() {
        assertThat(DigestFields.toJcaAlgorithm("sha-512")).isEqualTo("SHA-512");
    }

    @Test
    void unknownAlgorithmToJcaThrows() {
        assertThatThrownBy(() -> DigestFields.toJcaAlgorithm("sha-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------- helpers

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }
}
