package enkan.util.sf;

import enkan.util.sf.SfValue.*;

import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

/**
 * Serializer for RFC 8941 Structured Field Values.
 *
 * <p>Follows the algorithms in RFC 8941 §4.1.
 *
 * @author kawasima
 */
final class SfSerializer {

    private SfSerializer() {}

    // --- List (§4.1.1) ---

    static String serializeList(SfList list) {
        StringBuilder sb = new StringBuilder();
        Iterator<SfMember> it = list.members().iterator();
        while (it.hasNext()) {
            SfMember member = it.next();
            serializeItemOrInnerList(sb, member);
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    // --- Dictionary (§4.1.2) ---

    static String serializeDictionary(SfDictionary dict) {
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, SfMember>> it = dict.members().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SfMember> entry = it.next();
            validateKey(entry.getKey());
            sb.append(entry.getKey());
            SfMember member = entry.getValue();
            if (member instanceof SfItem item && item.value() instanceof SfBoolean b && b.value()) {
                serializeParameters(sb, item.parameters());
            } else {
                sb.append('=');
                serializeItemOrInnerList(sb, member);
            }
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    // --- Item (§4.1.3) ---

    static String serializeItem(SfItem item) {
        StringBuilder sb = new StringBuilder();
        serializeBareItem(sb, item.value());
        serializeParameters(sb, item.parameters());
        return sb.toString();
    }

    // --- Item or Inner List ---

    private static void serializeItemOrInnerList(StringBuilder sb, SfMember member) {
        switch (member) {
            case SfInnerList innerList -> serializeInnerList(sb, innerList);
            case SfItem item -> {
                serializeBareItem(sb, item.value());
                serializeParameters(sb, item.parameters());
            }
        }
    }

    // --- Inner List (§4.1.1.1) ---

    private static void serializeInnerList(StringBuilder sb, SfInnerList innerList) {
        sb.append('(');
        Iterator<SfItem> it = innerList.items().iterator();
        while (it.hasNext()) {
            SfItem item = it.next();
            serializeBareItem(sb, item.value());
            serializeParameters(sb, item.parameters());
            if (it.hasNext()) {
                sb.append(' ');
            }
        }
        sb.append(')');
        serializeParameters(sb, innerList.parameters());
    }

    // --- Parameters (§4.1.1.2) ---

    private static void serializeParameters(StringBuilder sb, SfParameters params) {
        if (params.isEmpty()) return;
        for (Map.Entry<String, SfValue> entry : params.map().entrySet()) {
            sb.append(';');
            validateKey(entry.getKey());
            sb.append(entry.getKey());
            SfValue value = entry.getValue();
            if (!(value instanceof SfBoolean b && b.value())) {
                sb.append('=');
                serializeBareItem(sb, value);
            }
        }
    }

    // --- Bare Item (§4.1.3.1) ---

    private static void serializeBareItem(StringBuilder sb, SfValue value) {
        switch (value) {
            case SfInteger v -> sb.append(v.value());
            case SfDecimal v -> serializeDecimal(sb, v.value());
            case SfString v -> serializeString(sb, v.value());
            case SfToken v -> {
                validateToken(v.value());
                sb.append(v.value());
            }
            case SfByteSequence v -> {
                sb.append(':');
                sb.append(Base64.getEncoder().encodeToString(v.value()));
                sb.append(':');
            }
            case SfBoolean v -> sb.append(v.value() ? "?1" : "?0");
            case SfDate v -> {
                sb.append('@');
                sb.append(v.value());
            }
            case SfDisplayString v -> serializeDisplayString(sb, v.value());
        }
    }

    private static void serializeDecimal(StringBuilder sb, double value) {
        // RFC 8941 §4.1.5: round to 3 decimal places, remove trailing zeros
        long rounded = Math.round(value * 1000);
        long intPart = rounded / 1000;
        long fracPart = Math.abs(rounded % 1000);

        // Preserve negative sign for -0.xxx values where intPart rounds to 0
        if (intPart == 0 && Double.doubleToRawLongBits(value) < 0) {
            sb.append('-');
        }
        sb.append(intPart);
        sb.append('.');
        if (fracPart == 0) {
            sb.append('0');
        } else if (fracPart % 100 == 0) {
            sb.append(fracPart / 100);
        } else if (fracPart % 10 == 0) {
            sb.append(fracPart / 100);
            sb.append(fracPart / 10 % 10);
        } else {
            sb.append(fracPart / 100);
            sb.append(fracPart / 10 % 10);
            sb.append(fracPart % 10);
        }
    }

    private static void serializeString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                throw new IllegalArgumentException(
                        "SfString contains character outside VCHAR/SP range: 0x" + Integer.toHexString(c));
            }
            if (c == '\\' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
    }

    private static void serializeDisplayString(StringBuilder sb, String value) {
        sb.append('%');
        sb.append('"');
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '%' || cp == '"' || cp < 0x20 || cp > 0x7e) {
                // Percent-encode the UTF-8 bytes of the code point
                byte[] bytes = new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    sb.append('%');
                    sb.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16)));
                    sb.append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
                }
            } else {
                sb.appendCodePoint(cp);
            }
        }
        sb.append('"');
    }

    // --- Validation helpers ---

    // RFC 8941 §3.1.2: key = ( lcalpha / "*" ) *( lcalpha / DIGIT / "_" / "-" / "." / "*" )
    private static void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Dictionary/parameter key must not be empty");
        }
        char first = key.charAt(0);
        if (!isLcAlpha(first) && first != '*') {
            throw new IllegalArgumentException("Invalid key: must start with lcalpha or '*': " + key);
        }
        for (int i = 1; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!isLcAlpha(c) && !isDigit(c) && c != '_' && c != '-' && c != '.' && c != '*') {
                throw new IllegalArgumentException("Invalid character in key: '" + c + "' in " + key);
            }
        }
    }

    // RFC 8941 §3.3.4: token = ( ALPHA / "*" ) *( tchar / ":" / "/" )
    private static void validateToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token must not be empty");
        }
        char first = token.charAt(0);
        if (!isAlpha(first) && first != '*') {
            throw new IllegalArgumentException("Invalid token: must start with ALPHA or '*': " + token);
        }
        for (int i = 1; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!isTchar(c) && c != ':' && c != '/') {
                throw new IllegalArgumentException("Invalid character in token: '" + c + "' in " + token);
            }
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlpha(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isLcAlpha(char c) {
        return c >= 'a' && c <= 'z';
    }

    // RFC 7230 tchar
    private static boolean isTchar(char c) {
        if (isAlpha(c) || isDigit(c)) return true;
        return switch (c) {
            case '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~' -> true;
            default -> false;
        };
    }
}
