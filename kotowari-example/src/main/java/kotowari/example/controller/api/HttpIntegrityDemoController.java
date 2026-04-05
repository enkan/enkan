package kotowari.example.controller.api;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import enkan.security.crypto.CryptoAlgorithm;
import enkan.security.crypto.JcaSigner;
import enkan.security.crypto.Signer;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.SignatureVerificationMiddleware;
import enkan.web.signature.HttpMessageSignatures;
import enkan.web.signature.SignatureAlgorithm;
import enkan.web.signature.SignatureComponent;
import enkan.web.signature.VerifyResult;
import enkan.web.http.fields.digest.DigestFields;

import kotowari.component.TemplateEngine;
import jakarta.inject.Inject;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates RFC 9530 (Digest Fields) + RFC 9421 (HTTP Message Signatures) usage.
 */
public class HttpIntegrityDemoController {
    public static final String VERIFY_PATH = "/api/http-integrity/verify";
    public static final String SAMPLE_PAYLOAD = "{\"message\":\"hello integrity\"}";
    public static final String KEY_ID = "demo-hmac-key";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Inject
    private TemplateEngine<?> templateEngine;

    // Demo secret for example only. Override with HTTP_INTEGRITY_DEMO_SECRET when needed.
    private static final SecretKey DEMO_KEY = new SecretKeySpec(
            System.getenv().getOrDefault("HTTP_INTEGRITY_DEMO_SECRET", "kotowari-demo-shared-secret")
                    .getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");

    public HttpResponse demoPage() {
        return templateEngine.render("recent/http-integrity-demo");
    }

    public HttpResponse sample(HttpRequest request) {
        String digestHeader = DigestFields.computeDigestHeader(
                SAMPLE_PAYLOAD.getBytes(StandardCharsets.UTF_8), "sha-256");

        DefaultHttpRequest signatureTarget = new DefaultHttpRequest();
        signatureTarget.setRequestMethod("POST");
        signatureTarget.setUri(VERIFY_PATH);
        signatureTarget.setScheme(request.getScheme());
        signatureTarget.setServerName(request.getServerName());
        signatureTarget.setServerPort(request.getServerPort());
        signatureTarget.setHeaders(Headers.of("content-digest", digestHeader));

        Signer signer = new JcaSigner(CryptoAlgorithm.HMAC_SHA256, DEMO_KEY);
        HttpMessageSignatures.SignatureResult signed = HttpMessageSignatures.sign(
                signatureTarget,
                List.of(
                        SignatureComponent.of("@method"),
                        SignatureComponent.of("@path"),
                        SignatureComponent.of("content-digest")
                ),
                SignatureAlgorithm.HMAC_SHA256,
                signer,
                KEY_ID,
                "rfc-demo");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Content-Digest", digestHeader);
        headers.put("Signature-Input", "sig1=" + signed.signatureInputValue());
        headers.put("Signature", "sig1=" + signed.signatureValue());

        String targetUrl = baseUrl(request) + VERIFY_PATH;
        String curl = "curl -i -X POST '" + targetUrl + "' "
                + "-H 'Content-Type: application/json' "
                + "-H 'Content-Digest: " + digestHeader + "' "
                + "-H 'Signature-Input: sig1=" + signed.signatureInputValue() + "' "
                + "-H 'Signature: sig1=" + signed.signatureValue() + "' "
                + "--data-binary '" + SAMPLE_PAYLOAD + "'";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", "RFC 9530 + RFC 9421 demo request to /api/http-integrity/verify");
        response.put("algorithm", "hmac-sha256");
        response.put("keyId", KEY_ID);
        response.put("coveredComponents", List.of("@method", "@path", "content-digest"));
        response.put("verifyPath", VERIFY_PATH);
        response.put("payload", SAMPLE_PAYLOAD);
        response.put("headers", headers);
        response.put("curl", curl);
        response.put("note", "The server verifies Content-Digest first, then Signature/Signature-Input.");
        return json(response);
    }

    @SuppressWarnings("unchecked")
    public HttpResponse verify(HttpRequest request, Object body) {
        List<VerifyResult> results = request.getExtension(SignatureVerificationMiddleware.EXTENSION_KEY);
        if (results == null) {
            results = List.of();
        }
        List<Map<String, Object>> signatures = results.stream().map(result -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", result.label());
            item.put("keyId", result.keyId());
            item.put("algorithm", result.algorithm() != null ? result.algorithm().sfName() : null);
            item.put("coveredValues", result.coveredValues());
            return item;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("verified", true);
        response.put("signatureCount", signatures.size());
        response.put("signatures", signatures);
        response.put("body", body);
        return json(response);
    }

    private static String baseUrl(HttpRequest request) {
        String host = request.getHeaders().get("host");
        if (host != null && !host.isBlank()) {
            return request.getScheme() + "://" + host;
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        String serverName = request.getServerName();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return defaultPort
                ? scheme + "://" + serverName
                : scheme + "://" + serverName + ":" + port;
    }

    private static HttpResponse json(Object payload) {
        try {
            return enkan.util.BeanBuilder.builder(HttpResponse.of(JSON.writeValueAsString(payload)))
                    .set(HttpResponse::setContentType, "application/json")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize demo response", e);
        }
    }
}
