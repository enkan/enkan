package enkan.web.signature;

import java.util.Map;

/**
 * Result of a successful HTTP message signature verification.
 *
 * <p>Contains the signature label, key identifier, algorithm, and a map of
 * the covered component values as they were used during verification.
 * Downstream consumers should prefer these values over raw headers to
 * ensure they are using data that was cryptographically verified.
 *
 * @param label         the signature label (e.g. {@code "sig1"})
 * @param keyId         the key identifier from {@code Signature-Input}
 * @param algorithm     the signature algorithm used
 * @param coveredValues the component identifier → value map used during verification
 * @author kawasima
 */
public record VerifyResult(
        String label,
        String keyId,
        SignatureAlgorithm algorithm,
        Map<String, String> coveredValues) {
}
