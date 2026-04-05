package enkan.security.crypto;

/**
 * Verifies a cryptographic signature against the original input.
 *
 * <p>Implementations may use HMAC (symmetric) or digital signatures (asymmetric).
 * HMAC implementations must use constant-time comparison to prevent timing attacks.
 *
 * @author kawasima
 */
@FunctionalInterface
public interface Verifier {

    /**
     * Verifies that the given signature is valid for the input.
     *
     * @param input     the original data that was signed
     * @param signature the signature to verify
     * @return {@code true} if the signature is valid
     */
    boolean verify(byte[] input, byte[] signature);
}
