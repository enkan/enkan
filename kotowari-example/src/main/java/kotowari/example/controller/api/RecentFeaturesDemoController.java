package kotowari.example.controller.api;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.jwt.JwsAlgorithm;
import enkan.web.jwt.JwtHeader;
import enkan.web.jwt.JwtProcessor;
import kotowari.component.TemplateEngine;
import jakarta.inject.Inject;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static enkan.util.BeanBuilder.builder;

/**
 * Demos for recently added web features (JWT and Idempotency-Key).
 */
public class RecentFeaturesDemoController {
    @Inject
    private TemplateEngine<?> templateEngine;

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String JWT_KEY_ID = "demo-jwt-key";
    private static final SecretKey JWT_KEY = new SecretKeySpec(
            System.getenv().getOrDefault("JWT_DEMO_SECRET", "kotowari-demo-jwt-secret")
                    .getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");

    public HttpResponse jwtDemoPage() {
        return templateEngine.render("recent/jwt-demo");
    }

    public HttpResponse idempotencyDemoPage() {
        return templateEngine.render("recent/idempotency-demo");
    }

    public HttpResponse idempotencySample(HttpRequest request) {
        String url = baseUrl(request) + "/api/recent/idempotency/echo";
        String key = "\"demo-idempotency-key-1\"";
        String payload = "{\"orderId\":\"A-1001\",\"amount\":1200}";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("description", "Replay-safe POST demo with Idempotency-Key middleware");
        out.put("note", "Idempotency-Key must be a Structured Field string, so use quoted value.");
        out.put("path", "/api/recent/idempotency/echo");
        out.put("firstRequest",
                "curl -i -X POST '" + url + "' "
                        + "-H 'Content-Type: application/json' "
                        + "-H 'Idempotency-Key: " + key + "' "
                        + "--data-binary '" + payload + "'");
        out.put("replayRequest",
                "curl -i -X POST '" + url + "' "
                        + "-H 'Content-Type: application/json' "
                        + "-H 'Idempotency-Key: " + key + "' "
                        + "--data-binary '" + payload + "'");
        return json(out, 200);
    }

    public HttpResponse idempotencyEcho(Parameters params, Object body) {
        long delayMs = Math.max(0L, params.getLong("delayMs", 0L));
        if (delayMs > 0) {
            try {
                Thread.sleep(Math.min(delayMs, 10_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("id", UUID.randomUUID().toString());
        out.put("processedAt", Instant.now().toString());
        out.put("body", body);
        out.put("delayMs", delayMs);
        return json(out, 200);
    }

    public HttpResponse jwtIssue(Parameters params, HttpRequest request) {
        String sub = params.get("sub");
        if (sub == null || sub.isBlank()) {
            sub = "alice";
        }

        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("scope", "read:demo");
        claims.put("iat", now);
        claims.put("exp", now + 300);

        try {
            byte[] claimsJson = JSON.writeValueAsBytes(claims);
            String token = JwtProcessor.sign(new JwtHeader("HS256", JWT_KEY_ID), claimsJson, JWT_KEY);

            String verifyUrl = baseUrl(request) + "/api/recent/jwt/verify";
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("description", "JWT issue demo (HS256)");
            out.put("token", token);
            out.put("claims", claims);
            out.put("verifyPath", "/api/recent/jwt/verify");
            out.put("verifyRequest",
                    "curl -i '" + verifyUrl + "' -H 'Authorization: Bearer " + token + "'");
            return json(out, 200);
        } catch (Exception e) {
            return json(Map.of("ok", false, "error", "Failed to issue token"), 500);
        }
    }

    public HttpResponse jwtVerify(HttpRequest request) {
        String auth = request.getHeaders().get("authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return json(Map.of("ok", false, "error", "Missing Authorization: Bearer <token>"), 401);
        }
        String token = auth.substring("Bearer ".length()).trim();
        byte[] payload = JwtProcessor.verify(token, JwsAlgorithm.HS256, JWT_KEY);
        if (payload == null) {
            return json(Map.of("ok", false, "error", "Invalid or expired JWT"), 401);
        }
        try {
            Object claims = JSON.readValue(payload, Object.class);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("claims", claims);
            out.put("header", JwtProcessor.decodeHeader(token));
            return json(out, 200);
        } catch (Exception e) {
            return json(Map.of("ok", false, "error", "Failed to parse JWT payload"), 500);
        }
    }

    private static HttpResponse json(Object payload, int status) {
        try {
            return builder(HttpResponse.of(JSON.writeValueAsString(payload)))
                    .set(HttpResponse::setStatus, status)
                    .set(HttpResponse::setContentType, "application/json")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON response", e);
        }
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
}
