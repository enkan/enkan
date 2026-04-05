package enkan.web.signature;

import enkan.security.crypto.*;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.util.sf.*;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class HttpMessageSignaturesTest {

    // ----------------------------------------------------------- HMAC sign + verify roundtrip

    @Test
    void hmacSignAndVerifyRoundTrip() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        HttpRequest req = buildRequest("POST", "/api/data", null, "https", "example.com", 443);
        req.getHeaders().put("content-type", "application/json");

        List<SignatureComponent> components = List.of(
                SignatureComponent.of("@method"),
                SignatureComponent.of("@authority"),
                SignatureComponent.of("content-type")
        );

        Signer signer = new JcaSigner(CryptoAlgorithm.HMAC_SHA256, key);
        HttpMessageSignatures.SignatureResult result = HttpMessageSignatures.sign(
                req, components, SignatureAlgorithm.HMAC_SHA256, signer, "test-key", null);

        assertThat(result.signatureInputValue()).contains("@method");
        assertThat(result.signatureValue()).startsWith(":");

        // Set headers for verification
        req.getHeaders().put("Signature-Input", "sig1=" + result.signatureInputValue());
        req.getHeaders().put("Signature", "sig1=" + result.signatureValue());

        SignatureKeyResolver resolver = testResolver("test-key", SignatureAlgorithm.HMAC_SHA256,
                new JcaVerifier(CryptoAlgorithm.HMAC_SHA256, key));
        List<VerifyResult> verified = HttpMessageSignatures.verifyAll(req, resolver);

        assertThat(verified).hasSize(1);
        assertThat(verified.getFirst().label()).isEqualTo("sig1");
        assertThat(verified.getFirst().keyId()).isEqualTo("test-key");
        assertThat(verified.getFirst().coveredValues()).containsEntry("@method", "POST");
        assertThat(verified.getFirst().coveredValues()).containsEntry("@authority", "example.com");
        assertThat(verified.getFirst().coveredValues()).containsEntry("content-type", "application/json");
    }

    // ----------------------------------------------------------- Ed25519 roundtrip

    @Test
    void ed25519SignAndVerifyRoundTrip() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        HttpRequest req = buildRequest("GET", "/resource", null, "https", "api.example.com", 443);

        List<SignatureComponent> components = List.of(
                SignatureComponent.of("@method"),
                SignatureComponent.of("@path")
        );

        Signer signer = new JcaSigner(CryptoAlgorithm.ED25519, kp.getPrivate());
        HttpMessageSignatures.SignatureResult result = HttpMessageSignatures.sign(
                req, components, SignatureAlgorithm.ED25519, signer, "ed-key", null);

        req.getHeaders().put("Signature-Input", "sig1=" + result.signatureInputValue());
        req.getHeaders().put("Signature", "sig1=" + result.signatureValue());

        SignatureKeyResolver resolver = testResolver("ed-key", SignatureAlgorithm.ED25519,
                new JcaVerifier(CryptoAlgorithm.ED25519, kp.getPublic()));
        List<VerifyResult> verified = HttpMessageSignatures.verifyAll(req, resolver);

        assertThat(verified).hasSize(1);
    }

    // ----------------------------------------------------------- tamper detection

    @Test
    void verifyFailsWithTamperedHeader() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        HttpRequest req = buildRequest("GET", "/", null, "https", "example.com", 443);
        req.getHeaders().put("content-type", "application/json");

        List<SignatureComponent> components = List.of(
                SignatureComponent.of("@method"),
                SignatureComponent.of("content-type")
        );

        Signer signer = new JcaSigner(CryptoAlgorithm.HMAC_SHA256, key);
        HttpMessageSignatures.SignatureResult result = HttpMessageSignatures.sign(
                req, components, SignatureAlgorithm.HMAC_SHA256, signer, "key1", null);

        req.getHeaders().put("Signature-Input", "sig1=" + result.signatureInputValue());
        req.getHeaders().put("Signature", "sig1=" + result.signatureValue());

        // Tamper with a signed header
        req.getHeaders().put("content-type", "text/html");

        SignatureKeyResolver resolver = testResolver("key1", SignatureAlgorithm.HMAC_SHA256,
                new JcaVerifier(CryptoAlgorithm.HMAC_SHA256, key));
        List<VerifyResult> verified = HttpMessageSignatures.verifyAll(req, resolver);

        assertThat(verified).isEmpty();
    }

    // ----------------------------------------------------------- missing headers

    @Test
    void verifyAllReturnsEmptyWhenNoSignatureHeaders() {
        HttpRequest req = buildRequest("GET", "/", null, "http", "example.com", 80);
        List<VerifyResult> results = HttpMessageSignatures.verifyAll(req, emptyResolver());
        assertThat(results).isEmpty();
    }

    // ----------------------------------------------------------- unknown keyid

    @Test
    void verifyAllSkipsUnknownKeyId() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        HttpRequest req = buildRequest("GET", "/", null, "https", "example.com", 443);

        Signer signer = new JcaSigner(CryptoAlgorithm.HMAC_SHA256, key);
        HttpMessageSignatures.SignatureResult result = HttpMessageSignatures.sign(
                req, List.of(SignatureComponent.of("@method")),
                SignatureAlgorithm.HMAC_SHA256, signer, "known-key", null);

        req.getHeaders().put("Signature-Input", "sig1=" + result.signatureInputValue());
        req.getHeaders().put("Signature", "sig1=" + result.signatureValue());

        // Resolver doesn't know this key
        List<VerifyResult> results = HttpMessageSignatures.verifyAll(req, emptyResolver());
        assertThat(results).isEmpty();
    }

    // ----------------------------------------------------------- expired signature

    @Test
    void verifyAllRejectsExpiredSignature() throws Exception {
        SecretKey key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        HttpRequest req = buildRequest("GET", "/", null, "https", "example.com", 443);

        // Manually craft an expired Signature-Input
        long pastExpires = Instant.now().getEpochSecond() - 60;
        String sigInput = "(\"@method\");alg=\"hmac-sha256\";keyid=\"k1\";created=1000;expires=" + pastExpires;

        // Sign with the correct base
        Map<String, SfValue> paramMap = new LinkedHashMap<>();
        paramMap.put("alg", new SfValue.SfString("hmac-sha256"));
        paramMap.put("keyid", new SfValue.SfString("k1"));
        paramMap.put("created", new SfValue.SfInteger(1000));
        paramMap.put("expires", new SfValue.SfInteger(pastExpires));
        SfParameters params = new SfParameters(paramMap);

        String base = SignatureBaseBuilder.buildSignatureBase(
                req, List.of(SignatureComponent.of("@method")), params);
        byte[] sig = new JcaSigner(CryptoAlgorithm.HMAC_SHA256, key).sign(
                base.getBytes(StandardCharsets.UTF_8));

        String sigValue = StructuredFields.serializeItem(new SfItem(new SfValue.SfByteSequence(sig)));
        req.getHeaders().put("Signature-Input", "sig1=" + sigInput);
        req.getHeaders().put("Signature", "sig1=" + sigValue);

        SignatureKeyResolver resolver = testResolver("k1", SignatureAlgorithm.HMAC_SHA256,
                new JcaVerifier(CryptoAlgorithm.HMAC_SHA256, key));
        List<VerifyResult> results = HttpMessageSignatures.verifyAll(req, resolver);
        assertThat(results).isEmpty();
    }

    // ----------------------------------------------------------- helpers

    private static HttpRequest buildRequest(String method, String path, String query,
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

    private static SignatureKeyResolver testResolver(String expectedKeyId,
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
