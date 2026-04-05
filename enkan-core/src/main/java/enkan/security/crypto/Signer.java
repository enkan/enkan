package enkan.security.crypto;

/**
 * Signs a byte array and returns the signature bytes.
 *
 * <p>Implementations may use HMAC (symmetric) or digital signatures (asymmetric).
 * The interface is intentionally minimal to allow JCA, HSM, and remote KMS
 * implementations.
 *
 * @author kawasima
 */
@FunctionalInterface
public interface Signer {

    /**
     * Computes a cryptographic signature over the given input.
     *
     * @param input the data to sign
     * @return the signature bytes
     */
    byte[] sign(byte[] input);
}
