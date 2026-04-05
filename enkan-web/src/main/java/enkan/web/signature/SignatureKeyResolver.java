package enkan.web.signature;

import enkan.security.crypto.Signer;
import enkan.security.crypto.Verifier;

import java.util.Optional;

/**
 * Resolves cryptographic signers and verifiers by key identifier.
 *
 * <p>Implementations may look up keys from a keystore, database, configuration,
 * or remote KMS. Returning {@link Signer}/{@link Verifier} rather than raw keys
 * allows transparent HSM and remote signing integration.
 *
 * @author kawasima
 */
public interface SignatureKeyResolver {

    /**
     * Returns a verifier for the given key identifier and algorithm.
     *
     * @param keyId     the key identifier from the {@code Signature-Input} {@code keyid} parameter
     * @param algorithm the signature algorithm
     * @return the verifier, or empty if the key is unknown
     */
    Optional<Verifier> resolveVerifier(String keyId, SignatureAlgorithm algorithm);

    /**
     * Returns a signer for the given key identifier and algorithm.
     *
     * @param keyId     the key identifier
     * @param algorithm the signature algorithm
     * @return the signer, or empty if the key is unknown
     */
    Optional<Signer> resolveSigner(String keyId, SignatureAlgorithm algorithm);
}
