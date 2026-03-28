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

    @Test
    void acceptLanguageRangeMatchesMoreSpecificTag() {
        // RFC 4647 §3.4: range "en" matches tag "en-gb"
        Set<String> available = new HashSet<>(Collections.singletonList("en-gb"));
        assertThat(neg.bestAllowedLanguage("en", available))
                .isEqualTo("en-gb");
    }

    @Test
    void acceptLanguageRangeWithQuality() {
        // "fr" has higher q than "en", so "fr-ca" should be preferred
        Set<String> available = new HashSet<>(Arrays.asList("en-gb", "fr-ca"));
        assertThat(neg.bestAllowedLanguage("en;q=0.5, fr;q=1.0", available))
                .isEqualTo("fr-ca");
    }

    @Test
    void acceptLanguageExactMatchPreferredOverPrefixMatch() {
        // "en-gb" exact match (q=0.9) should beat "en" prefix match on "en-us" (q=0.5)
        Set<String> available = new HashSet<>(Arrays.asList("en-gb", "en-us"));
        assertThat(neg.bestAllowedLanguage("en-gb;q=0.9, en;q=0.5", available))
                .isEqualTo("en-gb");
    }

    @Test
    void acceptLanguageWildcardStillWorks() {
        Set<String> available = new HashSet<>(Arrays.asList("ja", "ko"));
        assertThat(neg.bestAllowedLanguage("*", available))
                .isNotNull();
    }

    @Test
    void parseQRejectsInvalidFormats() {
        // RFC 9110 §12.4.2: qvalue = ( "0" [ "." 0*3DIGIT ] ) / ( "1" [ "." 0*3("0") ] )
        assertThat(neg.parseQ("0.5")).isEqualTo(0.5);
        assertThat(neg.parseQ("1.0")).isEqualTo(1.0);
        assertThat(neg.parseQ("1.000")).isEqualTo(1.0);
        assertThat(neg.parseQ("0")).isEqualTo(0.0);
        // Invalid formats should return 0.0
        assertThat(neg.parseQ("1e-1")).isEqualTo(0.0);
        assertThat(neg.parseQ("0.1234")).isEqualTo(0.0);
        assertThat(neg.parseQ("2.0")).isEqualTo(0.0);
        assertThat(neg.parseQ("-0.5")).isEqualTo(0.0);
        assertThat(neg.parseQ(null)).isEqualTo(0.0);
    }

    @Test
    void specificMediaTypePreferredOverWildcardSubtype() {
        // text/html (specificity=2) should beat text/* (specificity=1) at same q
        Set<String> allowed = new HashSet<>(Arrays.asList("text/html"));
        MediaType mt = neg.bestAllowedContentType("text/*, text/html", allowed);
        assertThat(mt.getType()).isEqualTo("text");
        assertThat(mt.getSubtype()).isEqualTo("html");
    }

    @Test
    void specificMediaTypePreferredOverFullWildcard() {
        // text/html (specificity=2) should beat */* (specificity=0) at same q
        Set<String> allowed = new HashSet<>(Arrays.asList("text/html"));
        MediaType mt = neg.bestAllowedContentType("*/*, text/html", allowed);
        assertThat(mt.getType()).isEqualTo("text");
        assertThat(mt.getSubtype()).isEqualTo("html");
    }

    @Test
    void higherQWinsOverSpecificity() {
        // text/* has higher q (1.0) so it wins despite lower specificity.
        // Use a single allowed type so the matched subtype is deterministic.
        Set<String> allowed = new HashSet<>(Arrays.asList("text/plain"));
        MediaType mt = neg.bestAllowedContentType("text/html;q=0.5, text/*;q=1.0", allowed);
        assertThat(mt).isNotNull();
        assertThat(mt.getType()).isEqualTo("text");
        assertThat(mt.getSubtype()).isEqualTo("plain");
    }

    @Test
    void threeWaySpecificityOrdering() {
        // */*, text/*, text/html all q=1.0 — text/html should win
        Set<String> allowed = new HashSet<>(Arrays.asList("text/html", "application/json"));
        MediaType mt = neg.bestAllowedContentType("*/*, text/*, text/html", allowed);
        assertThat(mt.getType()).isEqualTo("text");
        assertThat(mt.getSubtype()).isEqualTo("html");
    }

    @Test
    void specificityWinsRegardlessOfOrder() {
        // Same as above but reversed order in Accept header
        Set<String> allowed = new HashSet<>(Arrays.asList("text/html", "application/json"));
        MediaType mt = neg.bestAllowedContentType("text/html, text/*, */*", allowed);
        assertThat(mt.getType()).isEqualTo("text");
        assertThat(mt.getSubtype()).isEqualTo("html");
    }

    @Test
    void quotedParameterWithEscapedQuote() {
        // Quoted-string parameter values may contain escaped quotes
        Set<String> allowedTypes = new HashSet<>(Arrays.asList("text/html", "text/plain"));
        // q parameter with a normal value should still work alongside quoted params
        MediaType mt = neg.bestAllowedContentType("text/html;q=0.5;ext=\"a\\\"b\", text/plain;q=1.0", allowedTypes);
        assertThat(mt).isNotNull();
        assertThat(mt.getSubtype()).isEqualTo("plain");
    }

    @Test
    void encodingNegotiationWithoutWildcardDoesNotThrow() {
        // Accept-Encoding: gzip with no wildcard — must not NPE
        Set<String> available = new HashSet<>(Arrays.asList("deflate"));
        String enc = neg.bestAllowedEncoding("gzip", available);
        assertThat(enc).isEqualTo("identity");
    }

    @Test
    void encodingNegotiationWithWildcard() {
        Set<String> available = new HashSet<>(Arrays.asList("gzip", "br"));
        String enc = neg.bestAllowedEncoding("*;q=0.5, gzip;q=1.0", available);
        assertThat(enc).isEqualTo("gzip");
    }

    @Test
    void encodingNegotiationExactMatch() {
        Set<String> available = new HashSet<>(Arrays.asList("gzip", "deflate"));
        String enc = neg.bestAllowedEncoding("gzip;q=1.0, deflate;q=0.5", available);
        assertThat(enc).isEqualTo("gzip");
    }

    @Test
    void encodingFallsBackToIdentity() {
        // br accepted but not available, gzip available but not accepted
        Set<String> available = new HashSet<>(Arrays.asList("gzip"));
        String enc = neg.bestAllowedEncoding("br", available);
        assertThat(enc).isEqualTo("identity");
    }

    @Test
    void encodingReturnsNullWhenIdentityRejected() {
        // identity explicitly rejected, no matching encoding available
        Set<String> available = new HashSet<>(Arrays.asList("deflate"));
        String enc = neg.bestAllowedEncoding("gzip, identity;q=0", available);
        assertThat(enc).isNull();
    }
}
