package enkan.web.util.sf;

import enkan.web.util.sf.SfValue.*;
import static enkan.web.util.sf.SfChars.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Index-based recursive descent parser for RFC 8941 Structured Field Values.
 *
 * <p>This parser does not use regular expressions. It scans the input string
 * character-by-character using {@code charAt()} and {@code substring()},
 * following the algorithms in RFC 8941 §4.2.
 *
 * @author kawasima
 */
final class SfParser {
    private final String input;
    private int pos;

    SfParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    // --- Top-level parse methods ---

    SfList parseList() {
        skipOWS();
        List<SfMember> members = new ArrayList<>();
        while (pos < input.length()) {
            members.add(parseItemOrInnerList());
            skipOWS();
            if (pos >= input.length()) {
                return new SfList(members);
            }
            expect(',');
            skipOWS();
            if (pos >= input.length()) {
                throw fail("Trailing comma in List");
            }
        }
        return new SfList(members);
    }

    SfDictionary parseDictionary() {
        skipOWS();
        LinkedHashMap<String, SfMember> members = new LinkedHashMap<>();
        while (pos < input.length()) {
            String key = parseKey();
            SfMember member;
            if (pos < input.length() && peek() == '=') {
                advance();
                member = parseItemOrInnerList();
            } else {
                SfParameters params = parseParameters();
                member = new SfItem(new SfBoolean(true), params);
            }
            members.put(key, member);
            skipOWS();
            if (pos >= input.length()) {
                return new SfDictionary(members);
            }
            expect(',');
            skipOWS();
            if (pos >= input.length()) {
                throw fail("Trailing comma in Dictionary");
            }
        }
        return new SfDictionary(members);
    }

    SfItem parseItem() {
        skipOWS();
        SfValue bareItem = parseBareItem();
        SfParameters params = parseParameters();
        skipOWS();
        return new SfItem(bareItem, params);
    }

    // --- Item or Inner List ---

    private SfMember parseItemOrInnerList() {
        if (pos < input.length() && peek() == '(') {
            return parseInnerList();
        }
        SfValue bareItem = parseBareItem();
        SfParameters params = parseParameters();
        return new SfItem(bareItem, params);
    }

    // --- Inner List (§4.2.1.2) ---

    private SfInnerList parseInnerList() {
        expect('(');
        List<SfItem> items = new ArrayList<>();
        while (pos < input.length()) {
            skipSP();
            if (peek() == ')') {
                advance();
                SfParameters params = parseParameters();
                return new SfInnerList(items, params);
            }
            SfValue bareItem = parseBareItem();
            SfParameters itemParams = parseParameters();
            items.add(new SfItem(bareItem, itemParams));
            if (pos < input.length() && peek() != ' ' && peek() != ')') {
                throw fail("Expected SP or ')' in Inner List");
            }
        }
        throw fail("Unterminated Inner List");
    }

    // --- Parameters (§4.2.3.2) ---

    SfParameters parseParameters() {
        if (pos >= input.length() || peek() != ';') {
            return SfParameters.EMPTY;
        }
        LinkedHashMap<String, SfValue> map = new LinkedHashMap<>();
        while (pos < input.length() && peek() == ';') {
            advance(); // consume ';'
            skipSP();
            String key = parseKey();
            SfValue value;
            if (pos < input.length() && peek() == '=') {
                advance();
                value = parseBareItem();
            } else {
                value = new SfBoolean(true);
            }
            map.put(key, value);
        }
        return new SfParameters(map);
    }

    // --- Bare Item (§4.2.3.1) ---

    private SfValue parseBareItem() {
        if (pos >= input.length()) {
            throw fail("Empty Bare Item");
        }
        char c = peek();
        if (c == '-' || isDigit(c)) {
            return parseIntegerOrDecimal();
        } else if (c == '"') {
            return parseString();
        } else if (isAlpha(c) || c == '*') {
            return parseToken();
        } else if (c == ':') {
            return parseByteSequence();
        } else if (c == '?') {
            return parseBoolean();
        } else if (c == '@') {
            return parseDate();
        } else if (c == '%') {
            return parseDisplayString();
        }
        throw fail("Unrecognized Bare Item type: '" + c + "'");
    }

    // --- Integer or Decimal (§4.2.4 / §4.2.5) ---

    private SfValue parseIntegerOrDecimal() {
        boolean isDecimal = false;
        int sign = 1;

        if (peek() == '-') {
            sign = -1;
            advance();
        }
        if (pos >= input.length() || !isDigit(peek())) {
            throw fail("Expected digit in number");
        }

        int digitStart = pos;
        int dotPos = -1;

        while (pos < input.length()) {
            char c = peek();
            if (isDigit(c)) {
                advance();
            } else if (c == '.' && !isDecimal) {
                int intPartLen = pos - digitStart;
                if (intPartLen > 12) {
                    throw fail("Integer part too long for Decimal");
                }
                isDecimal = true;
                dotPos = pos;
                advance();
            } else {
                break;
            }
        }

        String numberStr = input.substring(digitStart, pos);
        if (isDecimal) {
            if (numberStr.endsWith(".")) {
                throw fail("Decimal must not end with '.'");
            }
            int fracLen = pos - dotPos - 1;
            if (fracLen > 3) {
                throw fail("Decimal fraction too long (max 3 digits)");
            }
            if (numberStr.length() > 16) {
                throw fail("Decimal too long");
            }
            double val = Double.parseDouble(numberStr);
            return new SfDecimal(sign * val);
        } else {
            if (numberStr.length() > 15) {
                throw fail("Integer too long (max 15 digits)");
            }
            long val = Long.parseLong(numberStr);
            return new SfInteger(sign * val);
        }
    }

    // --- String (§4.2.6) ---

    private SfString parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = advance();
            if (c == '\\') {
                if (pos >= input.length()) {
                    throw fail("Unterminated escape in String");
                }
                char next = advance();
                if (next != '"' && next != '\\') {
                    throw fail("Invalid escape in String: '\\" + next + "'");
                }
                sb.append(next);
            } else if (c == '"') {
                return new SfString(sb.toString());
            } else if (c < 0x20 || c > 0x7e) {
                throw fail("Invalid character in String: 0x" + Integer.toHexString(c));
            } else {
                sb.append(c);
            }
        }
        throw fail("Unterminated String");
    }

    // --- Token (§4.2.7) ---

    private SfToken parseToken() {
        char first = peek();
        if (!isAlpha(first) && first != '*') {
            throw fail("Token must start with ALPHA or '*'");
        }
        int start = pos;
        advance();
        while (pos < input.length()) {
            char c = peek();
            if (isTchar(c) || c == ':' || c == '/') {
                advance();
            } else {
                break;
            }
        }
        return new SfToken(input.substring(start, pos));
    }

    // --- Byte Sequence (§4.2.8) ---

    private SfByteSequence parseByteSequence() {
        expect(':');
        int start = pos;
        while (pos < input.length() && peek() != ':') {
            char c = peek();
            if (!isAlpha(c) && !isDigit(c) && c != '+' && c != '/' && c != '=') {
                throw fail("Invalid character in Byte Sequence: '" + c + "'");
            }
            advance();
        }
        if (pos >= input.length()) {
            throw fail("Unterminated Byte Sequence");
        }
        String b64 = input.substring(start, pos);
        advance(); // consume closing ':'
        try {
            byte[] decoded = Base64.getDecoder().decode(b64);
            return new SfByteSequence(decoded);
        } catch (IllegalArgumentException e) {
            throw fail("Invalid base64 in Byte Sequence");
        }
    }

    // --- Boolean (§4.2.9) ---

    private SfBoolean parseBoolean() {
        expect('?');
        if (pos >= input.length()) {
            throw fail("Missing Boolean value");
        }
        char c = advance();
        if (c == '1') return new SfBoolean(true);
        if (c == '0') return new SfBoolean(false);
        throw fail("Invalid Boolean value: '" + c + "'");
    }

    // --- Date (RFC 9651 §4.2.10) ---

    private SfDate parseDate() {
        expect('@');
        int sign = 1;
        if (pos < input.length() && peek() == '-') {
            sign = -1;
            advance();
        }
        if (pos >= input.length() || !isDigit(peek())) {
            throw fail("Expected digit in Date");
        }
        int start = pos;
        while (pos < input.length() && isDigit(peek())) {
            advance();
        }
        String digits = input.substring(start, pos);
        if (digits.length() > 15) {
            throw fail("Date value too long");
        }
        return new SfDate(sign * Long.parseLong(digits));
    }

    // --- Display String (RFC 9651 §4.2.11) ---

    private SfDisplayString parseDisplayString() {
        expect('%');
        expect('"');
        StringBuilder sb = new StringBuilder();
        ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream();
        while (pos < input.length()) {
            char c = advance();
            if (c == '%') {
                if (pos + 1 >= input.length()) {
                    throw fail("Unterminated percent-encoding in Display String");
                }
                char h1 = advance();
                char h2 = advance();
                if (!isHexDigit(h1) || !isHexDigit(h2)) {
                    throw fail("Invalid percent-encoding in Display String");
                }
                pendingBytes.write((hexVal(h1) << 4) | hexVal(h2));
            } else {
                // Flush accumulated percent-encoded bytes as strict UTF-8
                if (pendingBytes.size() > 0) {
                    flushUtf8Bytes(pendingBytes, sb);
                    pendingBytes.reset();
                }
                if (c == '"') {
                    return new SfDisplayString(sb.toString());
                } else if (c < 0x20 || c > 0x7e) {
                    throw fail("Invalid character in Display String: 0x" + Integer.toHexString(c));
                } else {
                    sb.append(c);
                }
            }
        }
        throw fail("Unterminated Display String");
    }

    // --- Key (§4.2.3.3) ---

    private String parseKey() {
        if (pos >= input.length()) {
            throw fail("Empty Key");
        }
        char first = peek();
        if (!isLcAlpha(first) && first != '*') {
            throw fail("Key must start with lcalpha or '*'");
        }
        int start = pos;
        advance();
        while (pos < input.length()) {
            char c = peek();
            if (isLcAlpha(c) || isDigit(c) || c == '_' || c == '-' || c == '.' || c == '*') {
                advance();
            } else {
                break;
            }
        }
        return input.substring(start, pos);
    }

    // --- Character helpers ---

    private void flushUtf8Bytes(ByteArrayOutputStream bytes, StringBuilder sb) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes.toByteArray()));
            sb.append(decoded);
        } catch (java.nio.charset.CharacterCodingException e) {
            throw fail("Invalid UTF-8 in Display String percent-encoding");
        }
    }

    void ensureEmpty() {
        skipOWS();
        if (pos < input.length()) {
            throw fail("Unexpected trailing characters");
        }
    }

    private char peek() {
        return input.charAt(pos);
    }

    private char advance() {
        return input.charAt(pos++);
    }

    private void expect(char expected) {
        if (pos >= input.length() || input.charAt(pos) != expected) {
            throw fail("Expected '" + expected + "'");
        }
        pos++;
    }

    private void skipSP() {
        while (pos < input.length() && input.charAt(pos) == ' ') pos++;
    }

    private void skipOWS() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t') pos++;
            else break;
        }
    }

    private SfParseException fail(String msg) {
        return new SfParseException(msg + " at position " + pos);
    }

}
