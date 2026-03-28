package enkan.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utilities for HTTP ETag generation and comparison per RFC 9110 §8.8.
 *
 * @author kawasima
 */
public final class ETagUtils {

    private ETagUtils() {}

    /**
     * Generates a weak ETag from a response body.
     *
     * <p>Supports {@link String} and {@code byte[]} bodies. Returns {@code null}
     * for other types (e.g. {@link java.io.InputStream}).
     *
     * <p>The Content-Encoding is included in the hash to produce distinct ETags
     * for differently encoded representations (RFC 9110 §8.8.3.3).
     *
     * @param body            the response body
     * @param contentEncoding the Content-Encoding header value, or null
     * @return a weak ETag string, or null if the body type is not supported
     */
    public static String generateWeakETag(Object body, String contentEncoding) {
        byte[] data;
        if (body instanceof String s) {
            data = s.getBytes(StandardCharsets.UTF_8);
        } else if (body instanceof byte[] bytes) {
            data = bytes;
        } else {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (contentEncoding != null) {
                digest.update(contentEncoding.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            digest.update(data);
            byte[] hash = digest.digest();
            // Use first 16 bytes (128 bits) for a compact but collision-resistant tag
            String hex = HexFormat.of().formatHex(hash, 0, 16);
            return "W/\"" + hex + "\"";
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the JVM spec
            throw new AssertionError("SHA-256 not available", e);
        }
    }

    /**
     * Strong comparison function (RFC 9110 §8.8.3.2).
     *
     * <p>Two entity tags are equivalent if both are NOT weak and their
     * opaque-tags match character-by-character.
     *
     * @param etag1 first entity-tag
     * @param etag2 second entity-tag
     * @return true if the tags are strongly equivalent
     */
    public static boolean strongMatch(String etag1, String etag2) {
        if (etag1 == null || etag2 == null) return false;
        if (isWeak(etag1) || isWeak(etag2)) return false;
        return opaqueTag(etag1).equals(opaqueTag(etag2));
    }

    /**
     * Weak comparison function (RFC 9110 §8.8.3.2).
     *
     * <p>Two entity tags are equivalent if their opaque-tags match
     * character-by-character, regardless of either or both being tagged as weak.
     *
     * @param etag1 first entity-tag
     * @param etag2 second entity-tag
     * @return true if the tags are weakly equivalent
     */
    public static boolean weakMatch(String etag1, String etag2) {
        if (etag1 == null || etag2 == null) return false;
        return opaqueTag(etag1).equals(opaqueTag(etag2));
    }

    /**
     * Checks if an ETag matches an If-Match or If-None-Match header value.
     *
     * <p>Handles the {@code "*"} wildcard and comma-separated lists of entity-tags.
     *
     * @param headerValue    the header value (e.g. {@code "\"a\", \"b\""} or {@code "*"})
     * @param etag           the response's entity-tag
     * @param weakComparison true for If-None-Match (weak), false for If-Match (strong)
     * @return true if a match is found
     */
    public static boolean matchesHeader(String headerValue, String etag, boolean weakComparison) {
        if (headerValue == null || etag == null) return false;
        String trimmed = headerValue.strip();
        if ("*".equals(trimmed)) return true;
        for (String candidate : trimmed.split(",")) {
            String tag = candidate.strip();
            if (tag.isEmpty()) continue;
            if (weakComparison ? weakMatch(tag, etag) : strongMatch(tag, etag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the entity-tag has the weak prefix {@code W/}.
     */
    static boolean isWeak(String entityTag) {
        return entityTag.startsWith("W/");
    }

    /**
     * Extracts the opaque-tag from an entity-tag (strips {@code W/} prefix and quotes).
     */
    static String opaqueTag(String entityTag) {
        String tag = entityTag;
        if (tag.startsWith("W/")) {
            tag = tag.substring(2);
        }
        if (tag.length() >= 2 && tag.charAt(0) == '"' && tag.charAt(tag.length() - 1) == '"') {
            tag = tag.substring(1, tag.length() - 1);
        }
        return tag;
    }
}
