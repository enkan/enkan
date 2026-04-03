package enkan.util.sf;

import java.util.Base64;

/**
 * A bare value in an RFC 8941 Structured Field.
 *
 * @author kawasima
 */
public sealed interface SfValue {

    record SfInteger(long value) implements SfValue {}

    record SfDecimal(double value) implements SfValue {}

    record SfString(String value) implements SfValue {}

    record SfToken(String value) implements SfValue {}

    record SfByteSequence(byte[] value) implements SfValue {
        @Override
        public String toString() {
            return "SfByteSequence[:" + Base64.getEncoder().encodeToString(value) + ":]";
        }
    }

    record SfBoolean(boolean value) implements SfValue {}

    record SfDate(long value) implements SfValue {}

    record SfDisplayString(String value) implements SfValue {}
}
