package enkan.web.signature;

import enkan.security.crypto.Signer;
import enkan.security.crypto.Verifier;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;

import java.util.Optional;

import static enkan.util.BeanBuilder.builder;

/**
 * Shared test helpers for HTTP signature tests.
 */
final class SignatureTestSupport {

    private SignatureTestSupport() {}

    static HttpRequest buildRequest(String method, String path, String query,
                                    String scheme, String host, int port) {
        return builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, method)
                .set(HttpRequest::setUri, path)
                .set(HttpRequest::setQueryString, query)
                .set(HttpRequest::setScheme, scheme)
                .set(HttpRequest::setServerName, host)
                .set(HttpRequest::setServerPort, port)
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();
    }

    static SignatureKeyResolver emptyResolver() {
        return new SignatureKeyResolver() {
            @Override
            public Optional<Verifier> resolveVerifier(String keyId, SignatureAlgorithm algorithm) {
                return Optional.empty();
            }
            @Override
            public Optional<Signer> resolveSigner(String keyId, SignatureAlgorithm algorithm) {
                return Optional.empty();
            }
        };
    }

    static SignatureKeyResolver testResolver(String expectedKeyId,
                                             SignatureAlgorithm expectedAlg,
                                             Verifier verifier) {
        return new SignatureKeyResolver() {
            @Override
            public Optional<Verifier> resolveVerifier(String keyId, SignatureAlgorithm algorithm) {
                if (expectedKeyId.equals(keyId) && expectedAlg == algorithm) {
                    return Optional.of(verifier);
                }
                return Optional.empty();
            }
            @Override
            public Optional<Signer> resolveSigner(String keyId, SignatureAlgorithm algorithm) {
                return Optional.empty();
            }
        };
    }
}
