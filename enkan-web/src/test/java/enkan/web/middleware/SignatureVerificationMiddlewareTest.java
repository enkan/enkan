package enkan.web.middleware;

import enkan.Endpoint;
import enkan.MiddlewareChain;
import enkan.chain.DefaultMiddlewareChain;
import enkan.predicate.AnyPredicate;
import enkan.security.crypto.CryptoAlgorithm;
import enkan.security.crypto.JcaSigner;
import enkan.security.crypto.JcaVerifier;
import enkan.security.crypto.Signer;
import enkan.security.crypto.Verifier;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.signature.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class SignatureVerificationMiddlewareTest {

    private MiddlewareChain<HttpRequest, HttpResponse, ?, ?> chain;

    @BeforeEach
    void setup() {
        chain = new DefaultMiddlewareChain<>(new AnyPredicate<>(), null,
                (Endpoint<HttpRequest, HttpResponse>) req ->
                        builder(HttpResponse.of("ok")).build());
    }

    // ----------------------------------------------------------- valid signature passes

    @Test
    void validSignaturePassesThrough() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        HttpRequest req = buildRequest("GET", "/api");
        req.getHeaders().put("content-type", "application/json");

        // Sign the request
        List<SignatureComponent> components = List.of(
                SignatureComponent.of("@method"),
                SignatureComponent.of("content-type")
        );
        Signer signer = new JcaSigner(CryptoAlgorithm.HMAC_SHA256, key);
        HttpMessageSignatures.SignatureResult result = HttpMessageSignatures.sign(
                req, components, SignatureAlgorithm.HMAC_SHA256, signer, "k1", null);

        req.getHeaders().put("Signature-Input", "sig1=" + result.signatureInputValue());
        req.getHeaders().put("Signature", "sig1=" + result.signatureValue());

        SignatureVerificationMiddleware mw = new SignatureVerificationMiddleware(
                testResolver("k1", key));
        HttpResponse response = mw.handle(req, chain);

        assertThat(response.getStatus()).isEqualTo(200);

        List<VerifyResult> results = req.getExtension(SignatureVerificationMiddleware.EXTENSION_KEY);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().coveredValues()).containsKey("@method");
    }

    // ----------------------------------------------------------- invalid signature returns 401

    @Test
    void invalidSignatureReturns401() throws Exception {
        SecretKey key1 = KeyGenerator.getInstance("HmacSHA256").generateKey();
        SecretKey key2 = KeyGenerator.getInstance("HmacSHA256").generateKey();
        HttpRequest req = buildRequest("GET", "/api");

        Signer signer = new JcaSigner(CryptoAlgorithm.HMAC_SHA256, key1);
        HttpMessageSignatures.SignatureResult result = HttpMessageSignatures.sign(
                req, List.of(SignatureComponent.of("@method")),
                SignatureAlgorithm.HMAC_SHA256, signer, "k1", null);

        req.getHeaders().put("Signature-Input", "sig1=" + result.signatureInputValue());
        req.getHeaders().put("Signature", "sig1=" + result.signatureValue());

        // Verify with wrong key
        SignatureVerificationMiddleware mw = new SignatureVerificationMiddleware(
                testResolver("k1", key2));
        HttpResponse response = mw.handle(req, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ----------------------------------------------------------- missing headers

    @Test
    void missingHeadersWithRequiredLabelsReturns401() {
        HttpRequest req = buildRequest("GET", "/api");

        SignatureVerificationMiddleware mw = new SignatureVerificationMiddleware(emptyResolver());
        mw.setRequiredLabels(Set.of("sig1"));
        HttpResponse response = mw.handle(req, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat((String) response.getBody()).contains("Missing");
    }

    @Test
    void missingHeadersWithoutRequirementsPassesThrough() {
        HttpRequest req = buildRequest("GET", "/api");

        SignatureVerificationMiddleware mw = new SignatureVerificationMiddleware(emptyResolver());
        HttpResponse response = mw.handle(req, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ----------------------------------------------------------- required components

    @Test
    void missingRequiredComponentReturns401() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        HttpRequest req = buildRequest("GET", "/api");

        // Sign covering only @method
        Signer signer = new JcaSigner(CryptoAlgorithm.HMAC_SHA256, key);
        HttpMessageSignatures.SignatureResult result = HttpMessageSignatures.sign(
                req, List.of(SignatureComponent.of("@method")),
                SignatureAlgorithm.HMAC_SHA256, signer, "k1", null);

        req.getHeaders().put("Signature-Input", "sig1=" + result.signatureInputValue());
        req.getHeaders().put("Signature", "sig1=" + result.signatureValue());

        SignatureVerificationMiddleware mw = new SignatureVerificationMiddleware(
                testResolver("k1", key));
        mw.setRequiredComponents(Set.of("@method", "@authority"));
        HttpResponse response = mw.handle(req, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat((String) response.getBody()).contains("@authority");
    }

    // ----------------------------------------------------------- Accept-Signature negotiation

    @Test
    void acceptSignatureHeaderOnRejection() {
        HttpRequest req = buildRequest("GET", "/api");

        SignatureVerificationMiddleware mw = new SignatureVerificationMiddleware(emptyResolver());
        mw.setRequiredLabels(Set.of("sig1"));
        mw.setAcceptSignature("sig1",
                List.of(SignatureComponent.of("@method"), SignatureComponent.of("@authority")),
                SignatureAlgorithm.HMAC_SHA256, "server-key");

        HttpResponse response = mw.handle(req, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        String acceptSig = response.getHeaders().get("Accept-Signature");
        assertThat(acceptSig).isNotNull();
        assertThat(acceptSig).contains("@method");
        assertThat(acceptSig).contains("@authority");
        assertThat(acceptSig).contains("hmac-sha256");
        assertThat(acceptSig).contains("server-key");
    }

    @Test
    void noAcceptSignatureOn400() {
        HttpRequest req = buildRequest("GET", "/api");
        req.getHeaders().put("Signature-Input", "!!!malformed!!!");
        req.getHeaders().put("Signature", "!!!malformed!!!");

        SignatureVerificationMiddleware mw = new SignatureVerificationMiddleware(emptyResolver());
        mw.setRequiredLabels(Set.of("sig1"));
        mw.setAcceptSignature("sig1",
                List.of(SignatureComponent.of("@method")),
                SignatureAlgorithm.HMAC_SHA256, "k1");

        HttpResponse response = mw.handle(req, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) response.getHeaders().get("Accept-Signature")).isNull();
    }

    // ----------------------------------------------------------- helpers

    private static HttpRequest buildRequest(String method, String path) {
        return builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, method)
                .set(HttpRequest::setUri, path)
                .set(HttpRequest::setScheme, "https")
                .set(HttpRequest::setServerName, "example.com")
                .set(HttpRequest::setServerPort, 443)
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();
    }

    private static SignatureKeyResolver testResolver(String expectedKeyId, SecretKey key) {
        return new SignatureKeyResolver() {
            @Override
            public Optional<Verifier> resolveVerifier(String keyId, SignatureAlgorithm algorithm) {
                if (expectedKeyId.equals(keyId)) {
                    return Optional.of(new JcaVerifier(algorithm.crypto(), key));
                }
                return Optional.empty();
            }
            @Override
            public Optional<Signer> resolveSigner(String keyId, SignatureAlgorithm algorithm) {
                return Optional.empty();
            }
        };
    }

    private static SignatureKeyResolver emptyResolver() {
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
}
