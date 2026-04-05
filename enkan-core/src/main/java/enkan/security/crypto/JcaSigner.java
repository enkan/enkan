package enkan.security.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.interfaces.ECKey;
import java.security.spec.AlgorithmParameterSpec;

/**
 * JCA-based {@link Signer} implementation.
 *
 * <p>Supports HMAC (symmetric) and digital signature (asymmetric) algorithms.
 * For ECDSA, the JCA-produced DER-encoded signature is converted to the raw
 * R||S (IEEE P1363) format commonly used by protocols such as JWS and HTTP
 * Message Signatures.
 *
 * @author kawasima
 */
public final class JcaSigner implements Signer {

    private final CryptoAlgorithm algorithm;
    private final Key key;

    /**
     * Creates a signer.
     *
     * @param algorithm the cryptographic algorithm
     * @param key       a {@link javax.crypto.SecretKey} for HMAC or a {@link PrivateKey} for asymmetric
     */
    public JcaSigner(CryptoAlgorithm algorithm, Key key) {
        this.algorithm = algorithm;
        this.key = key;
    }

    @Override
    public byte[] sign(byte[] input) {
        try {
            if (algorithm.type() == CryptoAlgorithm.Type.SYMMETRIC) {
                return signHmac(input);
            } else {
                return signAsymmetric(input);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signing failed for " + algorithm, e);
        }
    }

    private byte[] signHmac(byte[] input) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(algorithm.jcaName());
        mac.init(new SecretKeySpec(key.getEncoded(), algorithm.jcaName()));
        return mac.doFinal(input);
    }

    private byte[] signAsymmetric(byte[] input) throws GeneralSecurityException {
        Signature sig = Signature.getInstance(algorithm.jcaName());
        AlgorithmParameterSpec paramSpec = algorithm.parameterSpec();
        if (paramSpec != null) {
            sig.setParameter(paramSpec);
        }
        sig.initSign((PrivateKey) key);
        sig.update(input);
        byte[] raw = sig.sign();

        if (isEcdsa()) {
            int keyBits = ((ECKey) key).getParams().getOrder().bitLength();
            raw = EcdsaUtils.derToP1363(raw, keyBits);
            if (raw == null) {
                throw new SignatureException("Failed to convert ECDSA DER to P1363");
            }
        }
        return raw;
    }

    private boolean isEcdsa() {
        return algorithm.jcaName().contains("ECDSA");
    }
}
