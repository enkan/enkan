package enkan.web.util;

/**
 * Declare pattern strings defined by some RFCs.
 *
 * @author kawasima
 */
public class ParsingUtils {
    // HTTP token: 1*<any CHAR except CTLs or tspecials>. See RFC2068.
    public static final String RE_TOKEN = "[!#$%&'\\*\\-+\\.0-9A-Z\\^_`a-z\\|~]+";

    // HTTP quoted-string: <\"> *<any TEXT except \"> <\">. See RFC2068.
    public static final String RE_QUOTED = "\"(\\\"|[^\"])*\"";

    public static final String RE_VALUE = RE_TOKEN + "|" + RE_QUOTED;
}
