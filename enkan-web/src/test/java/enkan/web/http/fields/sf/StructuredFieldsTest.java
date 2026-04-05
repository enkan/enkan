package enkan.web.http.fields.sf;

import enkan.web.http.fields.sf.SfValue.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredFieldsTest {

    // ============================
    // Item parsing
    // ============================

    @Nested
    class ParseItem {

        @Test
        void parseInteger() {
            SfItem item = StructuredFields.parseItem("42");
            assertThat(item.value()).isEqualTo(new SfInteger(42));
            assertThat(item.parameters().isEmpty()).isTrue();
        }

        @Test
        void parseNegativeInteger() {
            SfItem item = StructuredFields.parseItem("-7");
            assertThat(item.value()).isEqualTo(new SfInteger(-7));
        }

        @Test
        void parseZero() {
            SfItem item = StructuredFields.parseItem("0");
            assertThat(item.value()).isEqualTo(new SfInteger(0));
        }

        @Test
        void parseMaxInteger() {
            SfItem item = StructuredFields.parseItem("999999999999999");
            assertThat(item.value()).isEqualTo(new SfInteger(999999999999999L));
        }

        @Test
        void rejectIntegerTooLong() {
            assertThatThrownBy(() -> StructuredFields.parseItem("1234567890123456"))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void parseDecimal() {
            SfItem item = StructuredFields.parseItem("3.14");
            assertThat(item.value()).isInstanceOf(SfDecimal.class);
            assertThat(((SfDecimal) item.value()).value()).isEqualTo(3.14);
        }

        @Test
        void parseNegativeDecimal() {
            SfItem item = StructuredFields.parseItem("-2.5");
            assertThat(((SfDecimal) item.value()).value()).isEqualTo(-2.5);
        }

        @Test
        void parseDecimalOneDigitFraction() {
            SfItem item = StructuredFields.parseItem("1.0");
            assertThat(((SfDecimal) item.value()).value()).isEqualTo(1.0);
        }

        @Test
        void rejectDecimalEndingWithDot() {
            assertThatThrownBy(() -> StructuredFields.parseItem("3."))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void rejectDecimalFractionTooLong() {
            assertThatThrownBy(() -> StructuredFields.parseItem("1.1234"))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void parseString() {
            SfItem item = StructuredFields.parseItem("\"hello world\"");
            assertThat(item.value()).isEqualTo(new SfString("hello world"));
        }

        @Test
        void parseStringWithEscapedQuote() {
            SfItem item = StructuredFields.parseItem("\"he said \\\"hi\\\"\"");
            assertThat(item.value()).isEqualTo(new SfString("he said \"hi\""));
        }

        @Test
        void parseStringWithEscapedBackslash() {
            SfItem item = StructuredFields.parseItem("\"back\\\\slash\"");
            assertThat(item.value()).isEqualTo(new SfString("back\\slash"));
        }

        @Test
        void parseEmptyString() {
            SfItem item = StructuredFields.parseItem("\"\"");
            assertThat(item.value()).isEqualTo(new SfString(""));
        }

        @Test
        void rejectUnterminatedString() {
            assertThatThrownBy(() -> StructuredFields.parseItem("\"hello"))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void parseToken() {
            SfItem item = StructuredFields.parseItem("foo");
            assertThat(item.value()).isEqualTo(new SfToken("foo"));
        }

        @Test
        void parseTokenWithSpecialChars() {
            SfItem item = StructuredFields.parseItem("text/html");
            assertThat(item.value()).isEqualTo(new SfToken("text/html"));
        }

        @Test
        void parseTokenStartingWithStar() {
            SfItem item = StructuredFields.parseItem("*foo");
            assertThat(item.value()).isEqualTo(new SfToken("*foo"));
        }

        @Test
        void parseByteSequence() {
            SfItem item = StructuredFields.parseItem(":cHJldHR5IG1hY2hpbmU=:");
            assertThat(item.value()).isInstanceOf(SfByteSequence.class);
            byte[] decoded = ((SfByteSequence) item.value()).value();
            assertThat(new String(decoded, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("pretty machine");
        }

        @Test
        void parseEmptyByteSequence() {
            SfItem item = StructuredFields.parseItem("::");
            assertThat(((SfByteSequence) item.value()).value()).isEmpty();
        }

        @Test
        void rejectUnterminatedByteSequence() {
            assertThatThrownBy(() -> StructuredFields.parseItem(":abc"))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void parseBooleanTrue() {
            SfItem item = StructuredFields.parseItem("?1");
            assertThat(item.value()).isEqualTo(new SfBoolean(true));
        }

        @Test
        void parseBooleanFalse() {
            SfItem item = StructuredFields.parseItem("?0");
            assertThat(item.value()).isEqualTo(new SfBoolean(false));
        }

        @Test
        void rejectInvalidBoolean() {
            assertThatThrownBy(() -> StructuredFields.parseItem("?2"))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void parseDate() {
            SfItem item = StructuredFields.parseItem("@1659578233");
            assertThat(item.value()).isEqualTo(new SfDate(1659578233L));
        }

        @Test
        void parseNegativeDate() {
            SfItem item = StructuredFields.parseItem("@-1000");
            assertThat(item.value()).isEqualTo(new SfDate(-1000L));
        }

        @Test
        void parseDisplayString() {
            SfItem item = StructuredFields.parseItem("%\"hello\"");
            assertThat(item.value()).isEqualTo(new SfDisplayString("hello"));
        }

        @Test
        void parseDisplayStringWithPercentEncoding() {
            // %22 = '"'
            SfItem item = StructuredFields.parseItem("%\"a%22b\"");
            assertThat(item.value()).isEqualTo(new SfDisplayString("a\"b"));
        }

        @Test
        void parseItemWithParameters() {
            SfItem item = StructuredFields.parseItem("\"hello\";q=0.8;lang=en");
            assertThat(item.value()).isEqualTo(new SfString("hello"));
            assertThat(item.parameters().get("q")).isEqualTo(new SfDecimal(0.8));
            assertThat(item.parameters().get("lang")).isEqualTo(new SfToken("en"));
        }

        @Test
        void parseItemWithBooleanTrueParameter() {
            SfItem item = StructuredFields.parseItem("foo;bar");
            assertThat(item.value()).isEqualTo(new SfToken("foo"));
            assertThat(item.parameters().get("bar")).isEqualTo(new SfBoolean(true));
        }

        @Test
        void rejectNullInput() {
            assertThatThrownBy(() -> StructuredFields.parseItem(null))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void rejectEmptyInput() {
            assertThatThrownBy(() -> StructuredFields.parseItem(""))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void rejectTrailingGarbage() {
            assertThatThrownBy(() -> StructuredFields.parseItem("42 xxx"))
                    .isInstanceOf(SfParseException.class);
        }

        @Test
        void leadingAndTrailingSpacesAreIgnored() {
            SfItem item = StructuredFields.parseItem("  42  ");
            assertThat(item.value()).isEqualTo(new SfInteger(42));
        }
    }

    // ============================
    // List parsing
    // ============================

    @Nested
    class ParseList {

        @Test
        void parseSimpleList() {
            SfList list = StructuredFields.parseList("1, 2, 3");
            assertThat(list.size()).isEqualTo(3);
            assertThat(list.get(0, SfItem.class).value()).isEqualTo(new SfInteger(1));
            assertThat(list.get(1, SfItem.class).value()).isEqualTo(new SfInteger(2));
            assertThat(list.get(2, SfItem.class).value()).isEqualTo(new SfInteger(3));
        }

        @Test
        void parseMixedTypeList() {
            SfList list = StructuredFields.parseList("\"a\", ?1, 42");
            assertThat(list.get(0, SfItem.class).value()).isEqualTo(new SfString("a"));
            assertThat(list.get(1, SfItem.class).value()).isEqualTo(new SfBoolean(true));
            assertThat(list.get(2, SfItem.class).value()).isEqualTo(new SfInteger(42));
        }

        @Test
        void parseSingleItemList() {
            SfList list = StructuredFields.parseList("foo");
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0, SfItem.class).value()).isEqualTo(new SfToken("foo"));
        }

        @Test
        void parseListWithInnerList() {
            SfList list = StructuredFields.parseList("(1 2), 3");
            assertThat(list.size()).isEqualTo(2);
            SfInnerList inner = list.get(0, SfInnerList.class);
            assertThat(inner.items()).hasSize(2);
            assertThat(inner.items().get(0).value()).isEqualTo(new SfInteger(1));
            assertThat(inner.items().get(1).value()).isEqualTo(new SfInteger(2));
            assertThat(list.get(1, SfItem.class).value()).isEqualTo(new SfInteger(3));
        }

        @Test
        void parseInnerListWithParameters() {
            SfList list = StructuredFields.parseList("(\"a\" \"b\");q=0.5");
            SfInnerList inner = list.get(0, SfInnerList.class);
            assertThat(inner.items()).hasSize(2);
            assertThat(inner.parameters().get("q")).isEqualTo(new SfDecimal(0.5));
        }

        @Test
        void parseEmptyInnerList() {
            SfList list = StructuredFields.parseList("()");
            SfInnerList inner = list.get(0, SfInnerList.class);
            assertThat(inner.items()).isEmpty();
        }

        @Test
        void rejectTrailingComma() {
            assertThatThrownBy(() -> StructuredFields.parseList("1, 2,"))
                    .isInstanceOf(SfParseException.class);
        }
    }

    // ============================
    // Dictionary parsing
    // ============================

    @Nested
    class ParseDictionary {

        @Test
        void parseSimpleDictionary() {
            SfDictionary dict = StructuredFields.parseDictionary("a=1, b=2");
            assertThat(dict.get("a", SfItem.class).value()).isEqualTo(new SfInteger(1));
            assertThat(dict.get("b", SfItem.class).value()).isEqualTo(new SfInteger(2));
        }

        @Test
        void parseDictionaryWithBooleanTrue() {
            // Key without '=' implies boolean true value
            SfDictionary dict = StructuredFields.parseDictionary("a, b=2");
            SfItem a = dict.get("a", SfItem.class);
            assertThat(a.value()).isEqualTo(new SfBoolean(true));
            assertThat(dict.get("b", SfItem.class).value()).isEqualTo(new SfInteger(2));
        }

        @Test
        void parseDictionaryWithInnerList() {
            SfDictionary dict = StructuredFields.parseDictionary("sig=(\"@method\" \"@path\");keyid=\"key1\"");
            SfInnerList sig = dict.get("sig", SfInnerList.class);
            assertThat(sig.items()).hasSize(2);
            assertThat(sig.items().get(0).value()).isEqualTo(new SfString("@method"));
            assertThat(sig.items().get(1).value()).isEqualTo(new SfString("@path"));
            assertThat(sig.parameters().get("keyid")).isEqualTo(new SfString("key1"));
        }

        @Test
        void parseDictionaryDuplicateKeysLastWins() {
            SfDictionary dict = StructuredFields.parseDictionary("a=1, a=2");
            assertThat(dict.get("a", SfItem.class).value()).isEqualTo(new SfInteger(2));
            assertThat(dict.size()).isEqualTo(1);
        }

        @Test
        void parseDictionaryWithParameterizedValue() {
            SfDictionary dict = StructuredFields.parseDictionary("a=1;x=2, b=\"hi\"");
            SfItem a = dict.get("a", SfItem.class);
            assertThat(a.value()).isEqualTo(new SfInteger(1));
            assertThat(a.parameters().get("x")).isEqualTo(new SfInteger(2));
        }

        @Test
        void parseDictionaryBooleanKeyWithParameters() {
            SfDictionary dict = StructuredFields.parseDictionary("a;x=1");
            SfItem a = dict.get("a", SfItem.class);
            assertThat(a.value()).isEqualTo(new SfBoolean(true));
            assertThat(a.parameters().get("x")).isEqualTo(new SfInteger(1));
        }

        @Test
        void parseSignatureInputExample() {
            String input = "sig1=(\"@method\" \"@target-uri\");created=1618884473;keyid=\"test-key-ed25519\"";
            SfDictionary dict = StructuredFields.parseDictionary(input);
            SfInnerList sig1 = dict.get("sig1", SfInnerList.class);
            assertThat(sig1.items()).hasSize(2);
            assertThat(sig1.items().get(0).value()).isEqualTo(new SfString("@method"));
            assertThat(sig1.items().get(1).value()).isEqualTo(new SfString("@target-uri"));
            assertThat(sig1.parameters().get("created")).isEqualTo(new SfInteger(1618884473));
            assertThat(sig1.parameters().get("keyid")).isEqualTo(new SfString("test-key-ed25519"));
        }
    }

    // ============================
    // Serialization
    // ============================

    @Nested
    class Serialize {

        @Test
        void serializeInteger() {
            SfItem item = new SfItem(new SfInteger(42));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("42");
        }

        @Test
        void serializeDecimal() {
            SfItem item = new SfItem(new SfDecimal(3.14));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("3.14");
        }

        @Test
        void serializeDecimalTrailingZeros() {
            SfItem item = new SfItem(new SfDecimal(1.0));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("1.0");
        }

        @Test
        void serializeString() {
            SfItem item = new SfItem(new SfString("hello"));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("\"hello\"");
        }

        @Test
        void serializeStringWithEscape() {
            SfItem item = new SfItem(new SfString("he said \"hi\""));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("\"he said \\\"hi\\\"\"");
        }

        @Test
        void serializeToken() {
            SfItem item = new SfItem(new SfToken("text/html"));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("text/html");
        }

        @Test
        void serializeByteSequence() {
            SfItem item = new SfItem(new SfByteSequence("hello".getBytes()));
            String b64 = Base64.getEncoder().encodeToString("hello".getBytes());
            assertThat(StructuredFields.serializeItem(item)).isEqualTo(":" + b64 + ":");
        }

        @Test
        void serializeBooleanTrue() {
            SfItem item = new SfItem(new SfBoolean(true));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("?1");
        }

        @Test
        void serializeBooleanFalse() {
            SfItem item = new SfItem(new SfBoolean(false));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("?0");
        }

        @Test
        void serializeDate() {
            SfItem item = new SfItem(new SfDate(1659578233L));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("@1659578233");
        }

        @Test
        void serializeItemWithParameters() {
            LinkedHashMap<String, SfValue> params = new LinkedHashMap<>();
            params.put("q", new SfDecimal(0.8));
            params.put("lang", new SfToken("en"));
            SfItem item = new SfItem(new SfString("hello"), new SfParameters(params));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("\"hello\";q=0.8;lang=en");
        }

        @Test
        void serializeParameterBooleanTrue() {
            LinkedHashMap<String, SfValue> params = new LinkedHashMap<>();
            params.put("bar", new SfBoolean(true));
            SfItem item = new SfItem(new SfToken("foo"), new SfParameters(params));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("foo;bar");
        }

        @Test
        void serializeList() {
            SfList list = new SfList(List.of(
                    new SfItem(new SfInteger(1)),
                    new SfItem(new SfInteger(2)),
                    new SfItem(new SfInteger(3))));
            assertThat(StructuredFields.serializeList(list)).isEqualTo("1, 2, 3");
        }

        @Test
        void serializeListWithInnerList() {
            SfList list = new SfList(List.of(
                    new SfInnerList(List.of(
                            new SfItem(new SfInteger(1)),
                            new SfItem(new SfInteger(2)))),
                    new SfItem(new SfInteger(3))));
            assertThat(StructuredFields.serializeList(list)).isEqualTo("(1 2), 3");
        }

        @Test
        void serializeDictionary() {
            LinkedHashMap<String, SfMember> members = new LinkedHashMap<>();
            members.put("a", new SfItem(new SfInteger(1)));
            members.put("b", new SfItem(new SfInteger(2)));
            SfDictionary dict = new SfDictionary(members);
            assertThat(StructuredFields.serializeDictionary(dict)).isEqualTo("a=1, b=2");
        }

        @Test
        void serializeDictionaryBooleanTrueOmitsValue() {
            LinkedHashMap<String, SfMember> members = new LinkedHashMap<>();
            members.put("a", new SfItem(new SfBoolean(true)));
            SfDictionary dict = new SfDictionary(members);
            assertThat(StructuredFields.serializeDictionary(dict)).isEqualTo("a");
        }

        @Test
        void serializeDictionaryBooleanTrueWithParams() {
            LinkedHashMap<String, SfValue> params = new LinkedHashMap<>();
            params.put("x", new SfInteger(1));
            LinkedHashMap<String, SfMember> members = new LinkedHashMap<>();
            members.put("a", new SfItem(new SfBoolean(true), new SfParameters(params)));
            SfDictionary dict = new SfDictionary(members);
            assertThat(StructuredFields.serializeDictionary(dict)).isEqualTo("a;x=1");
        }
    }

    // ============================
    // Round-trip
    // ============================

    @Nested
    class RoundTrip {

        @Test
        void itemRoundTrip() {
            String original = "\"hello\";q=0.8";
            SfItem parsed = StructuredFields.parseItem(original);
            String serialized = StructuredFields.serializeItem(parsed);
            assertThat(serialized).isEqualTo(original);
        }

        @Test
        void listRoundTrip() {
            String original = "1, 2, 3";
            SfList parsed = StructuredFields.parseList(original);
            String serialized = StructuredFields.serializeList(parsed);
            assertThat(serialized).isEqualTo(original);
        }

        @Test
        void dictionaryRoundTrip() {
            String original = "a=1, b=\"hello\"";
            SfDictionary parsed = StructuredFields.parseDictionary(original);
            String serialized = StructuredFields.serializeDictionary(parsed);
            assertThat(serialized).isEqualTo(original);
        }

        @Test
        void signatureInputRoundTrip() {
            String original = "sig1=(\"@method\" \"@target-uri\");created=1618884473;keyid=\"test-key\"";
            SfDictionary parsed = StructuredFields.parseDictionary(original);
            String serialized = StructuredFields.serializeDictionary(parsed);
            assertThat(serialized).isEqualTo(original);
        }

        @Test
        void idempotencyKeyRoundTrip() {
            String original = "\"8e03978e-40d5-43e8-bc93-6894a57f9324\"";
            SfItem parsed = StructuredFields.parseItem(original);
            String serialized = StructuredFields.serializeItem(parsed);
            assertThat(serialized).isEqualTo(original);
        }

        @Test
        void priorityHeaderRoundTrip() {
            String original = "u=3, i";
            SfDictionary parsed = StructuredFields.parseDictionary(original);
            String serialized = StructuredFields.serializeDictionary(parsed);
            assertThat(serialized).isEqualTo(original);
        }
    }

    // ============================
    // Edge cases
    // ============================

    @Nested
    class EdgeCases {

        @Test
        void byteSequenceEquals() {
            SfByteSequence a = new SfByteSequence("hello".getBytes());
            SfByteSequence b = new SfByteSequence("hello".getBytes());
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void byteSequenceNotEquals() {
            SfByteSequence a = new SfByteSequence("hello".getBytes());
            SfByteSequence b = new SfByteSequence("world".getBytes());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void serializeNegativeZeroDecimal() {
            SfItem item = new SfItem(new SfDecimal(-0.0));
            assertThat(StructuredFields.serializeItem(item)).isEqualTo("-0.0");
        }

        @Test
        void serializeSmallNegativeDecimalRoundedToZero() {
            // -0.0004 rounds to -0.0 at 3 decimal places
            SfItem item = new SfItem(new SfDecimal(-0.0004));
            String result = StructuredFields.serializeItem(item);
            assertThat(result).isEqualTo("-0.0");
        }

        @Test
        void listMembersAreUnmodifiable() {
            SfList list = StructuredFields.parseList("1, 2");
            assertThatThrownBy(() -> list.members().add(new SfItem(new SfInteger(3))))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void dictionaryMembersAreDefensivelyCopied() {
            LinkedHashMap<String, SfMember> members = new LinkedHashMap<>();
            members.put("a", new SfItem(new SfInteger(1)));
            SfDictionary dict = new SfDictionary(members);
            // Mutate the original map — should not affect the dictionary
            members.put("b", new SfItem(new SfInteger(2)));
            assertThat(dict.size()).isEqualTo(1);
        }

        @Test
        void parametersAreDefensivelyCopied() {
            LinkedHashMap<String, SfValue> map = new LinkedHashMap<>();
            map.put("q", new SfDecimal(0.5));
            SfParameters params = new SfParameters(map);
            // Mutate the original map — should not affect the parameters
            map.put("x", new SfInteger(1));
            assertThat(params.size()).isEqualTo(1);
        }

        @Test
        void innerListItemsAreUnmodifiable() {
            SfList list = StructuredFields.parseList("(1 2)");
            SfInnerList inner = list.get(0, SfInnerList.class);
            assertThatThrownBy(() -> inner.items().add(new SfItem(new SfInteger(3))))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void displayStringMultiByteUtf8RoundTrip() {
            // é = U+00E9 = UTF-8 bytes C3 A9
            SfItem item = new SfItem(new SfDisplayString("café"));
            String serialized = StructuredFields.serializeItem(item);
            assertThat(serialized).isEqualTo("%\"caf%C3%A9\"");
            SfItem reparsed = StructuredFields.parseItem(serialized);
            assertThat(reparsed.value()).isEqualTo(new SfDisplayString("café"));
        }

        @Test
        void serializeStringRejectsNonVchar() {
            SfItem item = new SfItem(new SfString("hello\u0000world"));
            assertThatThrownBy(() -> StructuredFields.serializeItem(item))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parseEmptyListReturnsEmptyList() {
            SfList list = StructuredFields.parseList("");
            assertThat(list.size()).isZero();
        }

        @Test
        void parseEmptyDictionaryReturnsEmptyDictionary() {
            SfDictionary dict = StructuredFields.parseDictionary("");
            assertThat(dict.size()).isZero();
        }

        @Test
        void parseWhitespaceOnlyListReturnsEmptyList() {
            SfList list = StructuredFields.parseList("  \t  ");
            assertThat(list.size()).isZero();
        }

        @Test
        void serializeIntegerRejectsOutOfRange() {
            SfItem item = new SfItem(new SfInteger(1000000000000000L));
            assertThatThrownBy(() -> StructuredFields.serializeItem(item))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void serializeDecimalRejectsOutOfRange() {
            SfItem item = new SfItem(new SfDecimal(9999999999999.0));
            assertThatThrownBy(() -> StructuredFields.serializeItem(item))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void serializeNullItemThrows() {
            assertThatThrownBy(() -> StructuredFields.serializeItem(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void serializeNullListThrows() {
            assertThatThrownBy(() -> StructuredFields.serializeList(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void serializeNullDictionaryThrows() {
            assertThatThrownBy(() -> StructuredFields.serializeDictionary(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void serializeInvalidKeyThrows() {
            LinkedHashMap<String, SfMember> members = new LinkedHashMap<>();
            members.put("Invalid-Key", new SfItem(new SfInteger(1)));
            SfDictionary dict = new SfDictionary(members);
            assertThatThrownBy(() -> StructuredFields.serializeDictionary(dict))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void serializeInvalidTokenThrows() {
            SfItem item = new SfItem(new SfToken("invalid token"));
            assertThatThrownBy(() -> StructuredFields.serializeItem(item))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
