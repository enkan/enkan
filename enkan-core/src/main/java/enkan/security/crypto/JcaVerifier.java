package enkan.security.crypto;

import enkan.exception.MisconfigurationException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

/**
 * JCA-based {@link Verifier} implementation.
 *
 * <p>Supports HMAC (symmetric) and digital signature (asymmetric) algorithms.
 * For ECDSA, the raw R||S (IEEE P1363) signature is converted to DER encoding
 * before passing to the JCA verifier. HMAC verification uses
 * {@link MessageDigest#isEqual(byte[], byte[])} for constant-time comparison.
 *
 * @author kawasima
 */
public final class JcaVerifier implements Verifier {

    private final CryptoAlgorithm algorithm;
    private final Key key;

    /**
     * Creates a verifier.
     *
     * @param algorithm the cryptographic algorithm
     * @param key       a {@link javax.crypto.SecretKey} for HMAC or a {@link PublicKey} for asymmetric
     * @throws MisconfigurationException if the key type is incompatible with the algorithm
     */
    public JcaVerifier(CryptoAlgorithm algorithm, Key key) {
        if (algorithm.type() == CryptoAlgorithm.Type.SYMMETRIC && !(key instanceof SecretKey)) {
            throw new MisconfigurationException("crypto.INCOMPATIBLE_KEY_TYPE", algorithm, key.getClass().getSimpleName());
        }
        if (algorithm.type() == CryptoAlgorithm.Type.ASYMMETRIC && !(key instanceof PublicKey)) {
            throw new MisconfigurationException("crypto.INCOMPATIBLE_KEY_TYPE", algorithm, key.getClass().getSimpleName());
        }
        this.algorithm = algorithm;
        this.key = key;
    }

    @Override
    public boolean verify(byte[] input, byte[] signature) {
        try {
            if (algorithm.type() == CryptoAlgorithm.Type.SYMMETRIC) {
                return verifyHmac(input, signature);
            } else {
                return verifyAsymmetric(input, signature);
            }
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private boolean verifyHmac(byte[] input, byte[] signature) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(algorithm.jcaName());
        mac.init((SecretKey) key);
        byte[] expected = mac.doFinal(input);
        return MessageDigest.isEqual(expected, signature);
    }

    private boolean verifyAsymmetric(byte[] input, byte[] signature) throws GeneralSecurityException {
        byte[] sigBytes = signature;
        if (algorithm.isEcdsa()) {
            sigBytes = EcdsaUtils.p1363ToDer(signature);
            if (sigBytes == null) return false;
        }

        Signature sig = Signature.getInstance(algorithm.jcaName());
        AlgorithmParameterSpec paramSpec = algorithm.parameterSpec();
        if (paramSpec != null) {
            sig.setParameter(paramSpec);
        }
        sig.initVerify((PublicKey) key);
        sig.update(input);
        return sig.verify(sigBytes);
    }
}
