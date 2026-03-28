package enkan.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * The utility for formatting and parsing HTTP dates.
 *
 * <p>Formatting uses legacy {@link SimpleDateFormat} for backward compatibility.
 * Parsing uses {@code java.time} formatters and supports the three formats
 * defined in RFC 9110 §5.6.7 (IMF-fixdate, RFC 850, asctime).
 *
 * @author kawasima
 */
public enum HttpDateFormat {
    RFC822("EEE, dd MMM yyyy HH:mm:ss Z"),
    RFC1123("EEE, dd MMM yyyy HH:mm:ss zzz"),
    RFC1036("EEEE, dd-MMM-yy HH:mm:ss zzz"),
    ASCTIME("EEE MMM d HH:mm:ss yyyy");

    private final String formatStr;

    HttpDateFormat(String formatStr) {
        this.formatStr = formatStr;
    }

    private DateFormat formatter() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(formatStr, Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat;
    }

    public String format(Date d) {
        return formatter().format(d);
    }

    // --- Parsing (RFC 9110 §5.6.7) ---

    /** IMF-fixdate: {@code Sun, 06 Nov 1994 08:49:37 GMT}. */
    private static final DateTimeFormatter IMF_FIXDATE_PARSE = new DateTimeFormatterBuilder()
            .appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT)
            .appendLiteral(", ")
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral(' ')
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral(' ')
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(" GMT")
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT)
            .withZone(ZoneOffset.UTC);

    /** RFC 850 (obsolete): {@code Sunday, 06-Nov-94 08:49:37 GMT}. */
    private static final DateTimeFormatter RFC_850_PARSE = new DateTimeFormatterBuilder()
            .appendText(ChronoField.DAY_OF_WEEK, TextStyle.FULL)
            .appendLiteral(", ")
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('-')
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral('-')
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1950)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(" GMT")
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT)
            .withZone(ZoneOffset.UTC);

    /** asctime: {@code Sun Nov  6 08:49:37 1994}. */
    private static final DateTimeFormatter ASCTIME_PARSE = new DateTimeFormatterBuilder()
            .appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT)
            .appendLiteral(' ')
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral(' ')
            .padNext(2)
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(' ')
            .appendValue(ChronoField.YEAR, 4)
            .toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT)
            .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter[] PARSE_FORMATS = {
            IMF_FIXDATE_PARSE, RFC_850_PARSE, ASCTIME_PARSE
    };

    /**
     * Parses an HTTP-date string into an {@link Instant}.
     *
     * <p>Tries IMF-fixdate, RFC 850, and asctime formats in order. Returns
     * {@link Optional#empty()} if the value is {@code null}, blank, or does
     * not match any recognized format.
     *
     * @param httpDate the HTTP-date header value
     * @return the parsed instant, or empty if the value is not a valid HTTP-date
     */
    public static Optional<Instant> parse(String httpDate) {
        if (httpDate == null) {
            return Optional.empty();
        }
        String trimmed = httpDate.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        for (DateTimeFormatter fmt : PARSE_FORMATS) {
            try {
                return Optional.of(Instant.from(fmt.parse(trimmed)));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return Optional.empty();
    }
}
