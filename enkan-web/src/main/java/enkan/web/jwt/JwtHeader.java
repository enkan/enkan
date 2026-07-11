package enkan.web.jwt;

import org.jspecify.annotations.Nullable;

/**
 * JWT JOSE Header per RFC 7519 §5 / RFC 7515 §4.
 *
 * @param typ the token type, typically {@code "JWT"}
 * @param alg the signing algorithm (e.g. {@code "HS256"}, {@code "RS256"}, {@code "ES256"})
 * @param kid the optional key identifier
 * @author kawasima
 */
public record JwtHeader(@Nullable String typ, @Nullable String alg, @Nullable String kid) {

    public JwtHeader(String alg) {
        this("JWT", alg, null);
    }

    public JwtHeader(String alg, @Nullable String kid) {
        this("JWT", alg, kid);
    }
}
