package enkan.web.signature;

import enkan.security.crypto.CryptoAlgorithm;

/**
 * RFC 9421 HTTP Message Signatures algorithm identifiers mapped to {@link CryptoAlgorithm}.
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9421#section-3.3">RFC 9421 §3.3</a>
 */
public enum SignatureAlgorithm {

    HMAC_SHA256("hmac-sha256", CryptoAlgorithm.HMAC_SHA256),
    HMAC_SHA384("hmac-sha384", CryptoAlgorithm.HMAC_SHA384),
    HMAC_SHA512("hmac-sha512", CryptoAlgorithm.HMAC_SHA512),

    ED25519("ed25519", CryptoAlgorithm.ED25519),

    ECDSA_P256_SHA256("ecdsa-p256-sha256", CryptoAlgorithm.ECDSA_P256_SHA256),
    ECDSA_P384_SHA384("ecdsa-p384-sha384", CryptoAlgorithm.ECDSA_P384_SHA384),
    ECDSA_P521_SHA512("ecdsa-p521-sha512", CryptoAlgorithm.ECDSA_P521_SHA512),

    RSA_PSS_SHA256("rsa-pss-sha256", CryptoAlgorithm.RSA_PSS_SHA256),
    RSA_PSS_SHA384("rsa-pss-sha384", CryptoAlgorithm.RSA_PSS_SHA384),
    RSA_PSS_SHA512("rsa-pss-sha512", CryptoAlgorithm.RSA_PSS_SHA512),

    RSA_V1_5_SHA256("rsa-v1_5-sha256", CryptoAlgorithm.RSA_V1_5_SHA256),
    RSA_V1_5_SHA384("rsa-v1_5-sha384", CryptoAlgorithm.RSA_V1_5_SHA384),
    RSA_V1_5_SHA512("rsa-v1_5-sha512", CryptoAlgorithm.RSA_V1_5_SHA512);

    private final String sfName;
    private final CryptoAlgorithm crypto;

    SignatureAlgorithm(String sfName, CryptoAlgorithm crypto) {
        this.sfName = sfName;
        this.crypto = crypto;
    }

    /** Returns the Structured Fields algorithm name used in {@code Signature-Input}. */
    public String sfName() {
        return sfName;
    }

    /** Returns the corresponding {@link CryptoAlgorithm}. */
    public CryptoAlgorithm crypto() {
        return crypto;
    }

    /**
     * Resolves an SF algorithm name to the corresponding enum constant.
     *
     * @param sfName the algorithm name from the {@code Signature-Input} header
     * @return the matching algorithm
     * @throws IllegalArgumentException if the name is unknown
     */
    public static SignatureAlgorithm fromSfName(String sfName) {
        for (SignatureAlgorithm alg : values()) {
            if (alg.sfName.equals(sfName)) return alg;
        }
        throw new IllegalArgumentException("Unknown HTTP signature algorithm: " + sfName);
    }
}
