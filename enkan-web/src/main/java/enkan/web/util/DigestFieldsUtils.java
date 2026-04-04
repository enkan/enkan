package enkan.web.util;

import enkan.web.util.sf.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for RFC 9530 Digest Fields ({@code Content-Digest} / {@code Repr-Digest}).
 *
 * <p>Computes and serializes digest headers using RFC 8941 Structured Fields Dictionary
 * format. Supports SHA-256 and SHA-512 algorithms.
 *
 * <p>Header format: {@code sha-256=:base64encodedvalue:}
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9530">RFC 9530</a>
 */
public final class DigestFieldsUtils {

    /** Algorithms supported by this implementation. */
    public static final Set<String> SUPPORTED_ALGORITHMS = Set.of("sha-256", "sha-512");

    private DigestFieldsUtils() {}

    /**
     * Computes the digest of {@code data} with the given algorithm and returns the
     * value as an RFC 9530 / RFC 8941 Structured Fields Dictionary string.
     *
     * <p>Example output: {@code sha-256=:47DEQpj...:}
     *
     * @param data      the bytes to hash
     * @param algorithm the SF algorithm name ({@code "sha-256"} or {@code "sha-512"})
     * @return the SF Dictionary header value string
     * @throws IllegalArgumentException if the algorithm is not supported
     */
    public static String computeDigestHeader(byte[] data, String algorithm) {
        if (!SUPPORTED_ALGORITHMS.contains(algorithm)) {
            throw new IllegalArgumentException("Unsupported digest algorithm: " + algorithm);
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(toJcaAlgorithm(algorithm));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(algorithm + " not available", e);
        }
        byte[] hash = digest.digest(data);

        Map<String, SfMember> members = new LinkedHashMap<>();
        members.put(algorithm, new SfItem(new SfValue.SfByteSequence(hash)));
        return StructuredFields.serializeDictionary(new SfDictionary(members));
    }

    /**
     * Selects the best algorithm from a {@code Want-Content-Digest} or
     * {@code Want-Repr-Digest} header value (RFC 9530 §4).
     *
     * <p>The header is an SF Dictionary mapping algorithm names to integer priorities.
     * This method returns the supported algorithm with the highest priority.
     * Returns {@code defaultAlgorithm} if the header is absent ({@code null}).
     * Returns {@code null} if the header is present but no supported algorithm is found
     * (indicating the digest header should be omitted).
     *
     * @param wantHeaderValue the raw {@code Want-Content-Digest} / {@code Want-Repr-Digest} header value,
     *                        or {@code null} if the header is absent
     * @param defaultAlgorithm the algorithm to use when the header is absent
     * @return the negotiated algorithm name, or {@code null} to omit the digest header
     */
    public static String negotiateAlgorithm(String wantHeaderValue, String defaultAlgorithm) {
        if (wantHeaderValue == null) return defaultAlgorithm;
        SfDictionary dict = StructuredFields.parseDictionary(wantHeaderValue);
        return dict.members().entrySet().stream()
                .filter(e -> SUPPORTED_ALGORITHMS.contains(e.getKey()))
                .filter(e -> e.getValue() instanceof SfItem item
                        && item.value() instanceof SfValue.SfInteger i
                        && i.value() > 0)  // RFC 9530 §4: priority 0 means "not wanted"
                .max(Comparator.comparingLong(
                        e -> ((SfValue.SfInteger) ((SfItem) e.getValue()).value()).value()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Converts an RFC 9530 algorithm name to a JCA algorithm name.
     *
     * @param sfAlgorithm e.g. {@code "sha-256"} or {@code "sha-512"}
     * @return JCA name e.g. {@code "SHA-256"}
     */
    public static String toJcaAlgorithm(String sfAlgorithm) {
        return switch (sfAlgorithm) {
            case "sha-256" -> "SHA-256";
            case "sha-512" -> "SHA-512";
            default -> throw new IllegalArgumentException("Unsupported: " + sfAlgorithm);
        };
    }
}
