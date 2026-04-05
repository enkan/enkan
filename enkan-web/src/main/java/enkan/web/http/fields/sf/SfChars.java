package enkan.web.http.fields.sf;

/**
 * Character classification helpers for RFC 8941 Structured Field parsing and serialization.
 *
 * @author kawasima
 */
final class SfChars {

    private SfChars() {}

    static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isAlpha(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    static boolean isLcAlpha(char c) {
        return c >= 'a' && c <= 'z';
    }

    static boolean isHexDigit(char c) {
        return isDigit(c) || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return c - 'a' + 10;
    }

    // RFC 7230 tchar: "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
    //                  "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
    static boolean isTchar(char c) {
        if (isAlpha(c) || isDigit(c)) return true;
        return switch (c) {
            case '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~' -> true;
            default -> false;
        };
    }
}
