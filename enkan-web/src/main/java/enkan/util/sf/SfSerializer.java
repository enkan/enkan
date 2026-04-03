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
        Iterator<Object> it = list.members().iterator();
        while (it.hasNext()) {
            Object member = it.next();
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
        Iterator<Map.Entry<String, Object>> it = dict.members().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            sb.append(entry.getKey());
            Object member = entry.getValue();
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

    private static void serializeItemOrInnerList(StringBuilder sb, Object member) {
        if (member instanceof SfInnerList innerList) {
            serializeInnerList(sb, innerList);
        } else if (member instanceof SfItem item) {
            serializeBareItem(sb, item.value());
            serializeParameters(sb, item.parameters());
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
            case SfToken v -> sb.append(v.value());
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
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' || c == '"' || c < 0x20 || c > 0x7e) {
                // Percent-encode
                byte[] bytes = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    sb.append('%');
                    sb.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16)));
                    sb.append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
                }
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
    }
}
