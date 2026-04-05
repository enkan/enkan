package enkan.security.crypto;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

/**
 * Cryptographic algorithms with JCA mappings.
 *
 * <p>Covers all algorithms needed for RFC 9421 HTTP Message Signatures and
 * JWS (RFC 7515). Each constant maps to a JCA algorithm name and indicates
 * whether it is symmetric (HMAC) or asymmetric (digital signature).
 *
 * @author kawasima
 */
public enum CryptoAlgorithm {

    HMAC_SHA256("HmacSHA256", Type.SYMMETRIC),
    HMAC_SHA384("HmacSHA384", Type.SYMMETRIC),
    HMAC_SHA512("HmacSHA512", Type.SYMMETRIC),

    ED25519("Ed25519", Type.ASYMMETRIC),

    ECDSA_P256_SHA256("SHA256withECDSA", Type.ASYMMETRIC),
    ECDSA_P384_SHA384("SHA384withECDSA", Type.ASYMMETRIC),
    ECDSA_P521_SHA512("SHA512withECDSA", Type.ASYMMETRIC),

    RSA_PSS_SHA256("RSASSA-PSS", Type.ASYMMETRIC),
    RSA_PSS_SHA384("RSASSA-PSS", Type.ASYMMETRIC),
    RSA_PSS_SHA512("RSASSA-PSS", Type.ASYMMETRIC),

    RSA_V1_5_SHA256("SHA256withRSA", Type.ASYMMETRIC),
    RSA_V1_5_SHA384("SHA384withRSA", Type.ASYMMETRIC),
    RSA_V1_5_SHA512("SHA512withRSA", Type.ASYMMETRIC);

    public enum Type { SYMMETRIC, ASYMMETRIC }

    private final String jcaName;
    private final Type type;

    CryptoAlgorithm(String jcaName, Type type) {
        this.jcaName = jcaName;
        this.type = type;
    }

    /** Returns the JCA algorithm name for use with {@code Mac} or {@code Signature}. */
    public String jcaName() {
        return jcaName;
    }

    public Type type() {
        return type;
    }

    /**
     * Returns the {@link AlgorithmParameterSpec} required by this algorithm,
     * or {@code null} if the JCA name alone is sufficient.
     *
     * <p>RSA-PSS variants require a {@link PSSParameterSpec} to select the
     * correct hash and MGF1 configuration.
     */
    public AlgorithmParameterSpec parameterSpec() {
        return switch (this) {
            case RSA_PSS_SHA256 -> new PSSParameterSpec("SHA-256", "MGF1",
                    MGF1ParameterSpec.SHA256, 32, 1);
            case RSA_PSS_SHA384 -> new PSSParameterSpec("SHA-384", "MGF1",
                    MGF1ParameterSpec.SHA384, 48, 1);
            case RSA_PSS_SHA512 -> new PSSParameterSpec("SHA-512", "MGF1",
                    MGF1ParameterSpec.SHA512, 64, 1);
            default -> null;
        };
    }
}
