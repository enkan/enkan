package enkan.web.jwt;

import enkan.security.crypto.CryptoAlgorithm;
import enkan.security.crypto.JcaSigner;
import enkan.security.crypto.JcaVerifier;
import enkan.security.crypto.Signer;
import enkan.security.crypto.Verifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Function;

/**
 * Stateless JWT (RFC 7519) processor for signing and verification.
 *
 * <p>Does not depend on Jackson or any JSON library directly. JSON serialization
 * and deserialization are provided by the caller via {@link Function} parameters,
 * allowing integration with any JSON library (Jackson, Gson, etc.).
 *
 * <p>The JOSE header is parsed using a minimal built-in JSON parser that only
 * extracts the {@code typ}, {@code alg}, and {@code kid} fields.
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7519">RFC 7519 — JSON Web Token</a>
 */
public final class JwtProcessor {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private JwtProcessor() {}

    /**
     * Signs a JWT with the given claims.
     *
     * @param header          the JOSE header
     * @param claimsJsonBytes the claims payload as JSON bytes
     * @param signer          the cryptographic signer
     * @return the signed JWT string (header.payload.signature)
     */
    public static String sign(JwtHeader header, byte[] claimsJsonBytes, Signer signer) {
        byte[] headerJson = serializeHeader(header);
        String encodedHeader = URL_ENCODER.encodeToString(headerJson);
        String encodedPayload = URL_ENCODER.encodeToString(claimsJsonBytes);

        byte[] signingInput = (encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII);
        byte[] signature = signer.sign(signingInput);
        String encodedSignature = URL_ENCODER.encodeToString(signature);

        return encodedHeader + "." + encodedPayload + "." + encodedSignature;
    }

    /**
     * Signs a JWT using a JCA key.
     *
     * @param header          the JOSE header
     * @param claimsJsonBytes the claims payload as JSON bytes
     * @param key             the signing key (SecretKey for HMAC, PrivateKey for asymmetric)
     * @return the signed JWT string
     */
    public static String sign(JwtHeader header, byte[] claimsJsonBytes, Key key) {
        JwsAlgorithm alg = JwsAlgorithm.fromJwsName(header.alg());
        return sign(header, claimsJsonBytes, new JcaSigner(alg.crypto(), key));
    }

    /**
     * Verifies a JWT and returns the decoded payload bytes.
     *
     * <p>Performs signature verification and time claim validation ({@code exp}, {@code nbf}).
     * Time claims are extracted from the payload using a minimal JSON number parser.
     * Returns {@code null} if verification fails for any reason.
     *
     * @param token    the JWT string (header.payload.signature)
     * @param verifier the cryptographic verifier
     * @return the decoded payload bytes, or {@code null} on failure
     */
    public static byte[] verify(String token, Verifier verifier) {
        if (token == null) return null;
        String[] parts = token.split("\\.", 4);
        if (parts.length != 3) return null;

        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] signature;
        try {
            signature = URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (!verifier.verify(signingInput, signature)) {
            return null;
        }

        byte[] payload;
        try {
            payload = URL_DECODER.decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (!validateTimeClaims(payload)) {
            return null;
        }

        return payload;
    }

    /**
     * Verifies a JWT using a JCA key, resolving the algorithm from the header.
     *
     * <p>The algorithm declared in the token header is validated against the key type
     * to prevent algorithm confusion attacks (e.g. CVE-2016-5431): symmetric keys
     * are only accepted with HMAC algorithms, and asymmetric keys only with
     * asymmetric algorithms. Returns {@code null} if the key type does not match
     * the algorithm family.
     *
     * @param token the JWT string
     * @param key   the verification key (SecretKey for HMAC, PublicKey for asymmetric)
     * @return the decoded payload bytes, or {@code null} on failure
     */
    public static byte[] verify(String token, Key key) {
        JwtHeader header = decodeHeader(token);
        if (header == null || header.alg() == null) return null;
        JwsAlgorithm alg;
        try {
            alg = JwsAlgorithm.fromJwsName(header.alg());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!isKeyTypeCompatible(alg.crypto(), key)) return null;
        return verify(token, new JcaVerifier(alg.crypto(), key));
    }

    /**
     * Verifies a JWT using a JCA key with an explicitly expected algorithm.
     *
     * <p>This is the recommended overload: the caller specifies the expected algorithm
     * rather than trusting the token header, fully preventing algorithm confusion attacks.
     *
     * @param token             the JWT string
     * @param expectedAlgorithm the algorithm the caller expects
     * @param key               the verification key
     * @return the decoded payload bytes, or {@code null} on failure
     */
    public static byte[] verify(String token, JwsAlgorithm expectedAlgorithm, Key key) {
        JwtHeader header = decodeHeader(token);
        if (header == null || header.alg() == null) return null;
        if (!expectedAlgorithm.jwsName().equals(header.alg())) return null;
        if (!isKeyTypeCompatible(expectedAlgorithm.crypto(), key)) return null;
        return verify(token, new JcaVerifier(expectedAlgorithm.crypto(), key));
    }

    /**
     * Verifies a JWT and deserializes the payload using the given deserializer.
     *
     * @param token        the JWT string
     * @param key          the verification key
     * @param deserializer converts payload JSON bytes to the desired type
     * @param <T>          the payload type
     * @return the deserialized payload, or {@code null} on failure
     */
    public static <T> T verify(String token, Key key, Function<byte[], T> deserializer) {
        byte[] payload = verify(token, key);
        if (payload == null) return null;
        return deserializer.apply(payload);
    }

    /**
     * Decodes the JOSE header from a JWT without verifying the signature.
     *
     * @param token the JWT string
     * @return the parsed header, or {@code null} if malformed
     */
    public static JwtHeader decodeHeader(String token) {
        if (token == null) return null;
        int dot = token.indexOf('.');
        if (dot <= 0) return null;
        try {
            byte[] headerJson = URL_DECODER.decode(token.substring(0, dot));
            return parseHeader(new String(headerJson, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Minimal JSON handling (no external library dependency)
    // -------------------------------------------------------------------------

    private static byte[] serializeHeader(JwtHeader header) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        boolean first = true;
        if (header.typ() != null) {
            sb.append("\"typ\":\"").append(escapeJson(header.typ())).append('"');
            first = false;
        }
        if (header.alg() != null) {
            if (!first) sb.append(',');
            sb.append("\"alg\":\"").append(escapeJson(header.alg())).append('"');
            first = false;
        }
        if (header.kid() != null) {
            if (!first) sb.append(',');
            sb.append("\"kid\":\"").append(escapeJson(header.kid())).append('"');
        }
        sb.append('}');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Minimal JSON parser for JOSE header — only extracts typ, alg, kid.
     *
     * <p>Limitations: uses naive {@code indexOf}-based key matching, which may
     * match keys that appear as substrings of other keys or inside string values.
     * This is acceptable because JOSE headers are machine-generated with a small,
     * well-known set of keys. The {@code extractJsonNumber} helper only handles
     * integer values (not decimal or exponential notation), consistent with
     * RFC 7519's NumericDate definition as an integer epoch.
     */
    static JwtHeader parseHeader(String json) {
        String typ = extractJsonString(json, "typ");
        String alg = extractJsonString(json, "alg");
        String kid = extractJsonString(json, "kid");
        return new JwtHeader(typ, alg, kid);
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = findClosingQuote(json, quoteStart + 1);
        if (quoteEnd < 0) return null;
        return unescapeJson(json.substring(quoteStart + 1, quoteEnd));
    }

    private static int findClosingQuote(String json, int from) {
        for (int i = from; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> { sb.append('\\'); sb.append(next); }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Default clock skew tolerance in seconds for exp/nbf validation. */
    private static final long DEFAULT_CLOCK_SKEW_SECONDS = 60;

    /**
     * Validates exp and nbf time claims from raw JSON payload bytes.
     * Uses minimal parsing to extract numeric values without a JSON library.
     * Allows a default tolerance of {@value #DEFAULT_CLOCK_SKEW_SECONDS} seconds
     * for clock skew in distributed systems.
     */
    private static boolean validateTimeClaims(byte[] payload) {
        String json = new String(payload, StandardCharsets.UTF_8);
        long now = Instant.now().getEpochSecond();

        Long exp = extractJsonNumber(json, "exp");
        if (exp != null && exp + DEFAULT_CLOCK_SKEW_SECONDS <= now) return false;

        Long nbf = extractJsonNumber(json, "nbf");
        if (nbf != null && nbf - DEFAULT_CLOCK_SKEW_SECONDS > now) return false;

        return true;
    }

    /**
     * Checks that the key type is compatible with the algorithm family.
     * Prevents algorithm confusion attacks where an asymmetric public key
     * is used as an HMAC secret (or vice versa).
     */
    private static boolean isKeyTypeCompatible(CryptoAlgorithm crypto, Key key) {
        if (crypto.type() == CryptoAlgorithm.Type.SYMMETRIC) {
            return key instanceof SecretKey;
        } else {
            return key instanceof PublicKey;
        }
    }

    private static Long extractJsonNumber(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == start) return null;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
