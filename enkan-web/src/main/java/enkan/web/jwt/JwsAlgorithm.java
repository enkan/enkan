package enkan.web.jwt;

import enkan.security.crypto.CryptoAlgorithm;

/**
 * JWS (RFC 7515) algorithm identifiers mapped to {@link CryptoAlgorithm}.
 *
 * <p>Each constant maps the JWS {@code alg} header parameter value to
 * the corresponding cryptographic algorithm in the shared crypto layer.
 *
 * @author kawasima
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7518#section-3.1">RFC 7518 §3.1</a>
 */
public enum JwsAlgorithm {

    HS256("HS256", CryptoAlgorithm.HMAC_SHA256),
    HS384("HS384", CryptoAlgorithm.HMAC_SHA384),
    HS512("HS512", CryptoAlgorithm.HMAC_SHA512),

    RS256("RS256", CryptoAlgorithm.RSA_V1_5_SHA256),
    RS384("RS384", CryptoAlgorithm.RSA_V1_5_SHA384),
    RS512("RS512", CryptoAlgorithm.RSA_V1_5_SHA512),

    PS256("PS256", CryptoAlgorithm.RSA_PSS_SHA256),
    PS384("PS384", CryptoAlgorithm.RSA_PSS_SHA384),
    PS512("PS512", CryptoAlgorithm.RSA_PSS_SHA512),

    ES256("ES256", CryptoAlgorithm.ECDSA_P256_SHA256),
    ES384("ES384", CryptoAlgorithm.ECDSA_P384_SHA384),
    ES512("ES512", CryptoAlgorithm.ECDSA_P521_SHA512),

    EdDSA("EdDSA", CryptoAlgorithm.ED25519);

    private final String jwsName;
    private final CryptoAlgorithm crypto;

    JwsAlgorithm(String jwsName, CryptoAlgorithm crypto) {
        this.jwsName = jwsName;
        this.crypto = crypto;
    }

    /** Returns the JWS {@code alg} header value (e.g. {@code "HS256"}). */
    public String jwsName() {
        return jwsName;
    }

    /** Returns the corresponding {@link CryptoAlgorithm}. */
    public CryptoAlgorithm crypto() {
        return crypto;
    }

    /**
     * Resolves a JWS algorithm name to the corresponding enum constant.
     *
     * @param jwsName the {@code alg} header value
     * @return the matching algorithm
     * @throws IllegalArgumentException if the name is unknown or {@code "none"}
     */
    public static JwsAlgorithm fromJwsName(String jwsName) {
        if ("none".equals(jwsName)) {
            throw new IllegalArgumentException("Algorithm 'none' is not allowed");
        }
        for (JwsAlgorithm alg : values()) {
            if (alg.jwsName.equals(jwsName)) return alg;
        }
        throw new IllegalArgumentException("Unknown JWS algorithm: " + jwsName);
    }
}
