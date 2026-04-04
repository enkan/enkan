package enkan.web.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class HttpDateFormatTest {
    @Test
    void test() {
        Date d = new Date(1234556789012L);
        assertThat(HttpDateFormat.RFC822.format(d))
                .isEqualTo("Fri, 13 Feb 2009 20:26:29 +0000");
        assertThat(HttpDateFormat.ASCTIME.format(d))
                .isEqualTo("Fri Feb 13 20:26:29 2009");
        assertThat(HttpDateFormat.RFC1036.format(d))
                .isEqualTo("Friday, 13-Feb-09 20:26:29 GMT");
        assertThat(HttpDateFormat.RFC1123.format(d))
                .isEqualTo("Fri, 13 Feb 2009 20:26:29 GMT");
    }

    @Test
    void parseImfFixdate() {
        Optional<Instant> result = HttpDateFormat.parse("Sun, 06 Nov 1994 08:49:37 GMT");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    @Test
    void parseRfc850() {
        Optional<Instant> result = HttpDateFormat.parse("Sunday, 06-Nov-94 08:49:37 GMT");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    @Test
    void parseAsctime() {
        Optional<Instant> result = HttpDateFormat.parse("Sun Nov  6 08:49:37 1994");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    @Test
    void parseNullReturnsEmpty() {
        assertThat(HttpDateFormat.parse(null)).isEmpty();
    }

    @Test
    void parseBlankReturnsEmpty() {
        assertThat(HttpDateFormat.parse("")).isEmpty();
        assertThat(HttpDateFormat.parse("   ")).isEmpty();
    }

    @Test
    void parseMalformedReturnsEmpty() {
        assertThat(HttpDateFormat.parse("not a date")).isEmpty();
    }

    @Test
    void parseNonGmtReturnsEmpty() {
        assertThat(HttpDateFormat.parse("Sun, 06 Nov 1994 08:49:37 PST")).isEmpty();
    }

    @Test
    void parseInvalidDateReturnsEmpty() {
        // Feb 30 does not exist
        assertThat(HttpDateFormat.parse("Wed, 30 Feb 1994 08:49:37 GMT")).isEmpty();
    }

    @Test
    void parseStripsLeadingAndTrailingWhitespace() {
        Optional<Instant> result = HttpDateFormat.parse("  Sun, 06 Nov 1994 08:49:37 GMT  ");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    @Test
    void parseRfc850TwoDigitYearBoundary() {
        // year=49 → 2049
        Optional<Instant> r1 = HttpDateFormat.parse("Saturday, 06-Nov-49 08:49:37 GMT");
        assertThat(r1).isPresent();
        assertThat(r1.get().toString()).startsWith("2049-");
        // year=50 → 1950
        Optional<Instant> r2 = HttpDateFormat.parse("Saturday, 04-Nov-50 08:49:37 GMT");
        assertThat(r2).isPresent();
        assertThat(r2.get().toString()).startsWith("1950-");
    }
}
