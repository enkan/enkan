package enkan.middleware.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class AcceptHeaderNegotiatorTest {
    private AcceptHeaderNegotiator neg;

    @BeforeEach
    void setup() {
        neg = new AcceptHeaderNegotiator();
    }

    @Test
    void acceptFragment() {
        Set<String> allowedTypes = new HashSet<>(Collections.singletonList("text/html"));
        MediaType mt = neg.bestAllowedContentType("text/plain; q=0.8", allowedTypes);
        assertThat(mt.getType()).isEqualTo("text");
        assertThat(mt.getSubtype()).isEqualTo("plain");
    }

    @Test
    void acceptCharsetMatchesStandardName() {
        Set<String> available = new HashSet<>(Arrays.asList("utf-8", "iso-8859-1"));
        assertThat(neg.bestAllowedCharset("iso-8859-1", available))
                .isEqualTo("iso-8859-1");
    }

    @Test
    void acceptCharsetMatchesAlias() {
        // Client sends "latin1" which is an alias for iso-8859-1
        Set<String> available = new HashSet<>(Arrays.asList("utf-8", "iso-8859-1"));
        assertThat(neg.bestAllowedCharset("latin1;q=1.0, utf-8;q=0.5", available))
                .isEqualTo("iso-8859-1");
    }

    @Test
    void acceptCharsetDefaultsIso88591() {
        // RFC 9110 §12.5.3: ISO-8859-1 gets default quality 1.0 when not listed
        Set<String> available = new HashSet<>(Arrays.asList("utf-8", "iso-8859-1"));
        assertThat(neg.bestAllowedCharset("utf-8;q=0.5", available))
                .isEqualTo("iso-8859-1");
    }

    @Test
    void acceptCharsetWildcard() {
        // "*" in Accept-Charset applies to any charset not explicitly listed
        Set<String> available = new HashSet<>(Arrays.asList("utf-8", "shift_jis"));
        assertThat(neg.bestAllowedCharset("utf-8;q=0.5, *;q=0.1", available))
                .isEqualTo("utf-8");
        assertThat(neg.bestAllowedCharset("*;q=0.5", available))
                .isNotNull();
    }

    @Test
    void acceptCharsetCaseInsensitive() {
        // RFC 9110: charset comparison is case-insensitive
        Set<String> available = new HashSet<>(Arrays.asList("utf-8", "iso-8859-1"));
        assertThat(neg.bestAllowedCharset("UTF-8;q=1.0, ISO-8859-1;q=0.5", available))
                .isEqualTo("utf-8");
    }

    @Test
    void acceptLanguage() {
        Set<String> allowedLangs = new HashSet<>(Arrays.asList("da", "en-gb", "en"));
        assertThat(neg.bestAllowedLanguage("da, en-gb;q=0.8, en; q=0.7", allowedLangs))
                .isEqualTo("da");

        allowedLangs = new HashSet<>(Arrays.asList("en-gb", "en"));
        assertThat(neg.bestAllowedLanguage("da, en-gb;q=0.8, en; q=0.7", allowedLangs))
                .isEqualTo("en-gb");

        allowedLangs = new HashSet<>(Collections.singletonList("en"));
        assertThat(neg.bestAllowedLanguage("da, en-gb;q=0.8, en; q=0.7", allowedLangs))
                .isEqualTo("en");

        allowedLangs = new HashSet<>(Collections.singletonList("en-cockney"));
        assertThat(neg.bestAllowedLanguage("da, en-gb;q=0.8", allowedLangs))
                .isNull();
    }
}
