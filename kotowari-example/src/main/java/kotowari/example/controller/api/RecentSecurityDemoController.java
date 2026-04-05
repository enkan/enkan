package kotowari.example.controller.api;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import enkan.collection.Parameters;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.middleware.CspNonceMiddleware;
import kotowari.component.TemplateEngine;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

import static enkan.util.BeanBuilder.builder;

/**
 * HTML + JSON demos for recent security/runtime middlewares.
 */
public class RecentSecurityDemoController {
    public static final String CSP_NONCE_PAGE = "/recent/security/csp-nonce";
    public static final String REQUEST_TIMEOUT_PAGE = "/recent/security/request-timeout";
    public static final String FETCH_METADATA_PAGE = "/recent/security/fetch-metadata";
    public static final String TIMEOUT_ECHO_API = "/api/recent/security/timeout-echo";
    public static final String FETCH_METADATA_ECHO_API = "/api/recent/security/fetch-metadata-echo";

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Inject
    private TemplateEngine<?> templateEngine;

    public HttpResponse cspNoncePage(HttpRequest request) {
        String nonce = request.getExtension(CspNonceMiddleware.EXTENSION_KEY);
        return templateEngine.render("recent/security/csp-nonce",
                "nonce", nonce,
                "apiPath", TIMEOUT_ECHO_API);
    }

    public HttpResponse requestTimeoutPage() {
        return templateEngine.render("recent/security/request-timeout",
                "timeoutMs", 200,
                "apiPath", TIMEOUT_ECHO_API);
    }

    public HttpResponse fetchMetadataPage(HttpRequest request) {
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("sec-fetch-site", request.getHeaders().get("sec-fetch-site"));
        observed.put("sec-fetch-mode", request.getHeaders().get("sec-fetch-mode"));
        observed.put("sec-fetch-dest", request.getHeaders().get("sec-fetch-dest"));
        observed.put("sec-fetch-user", request.getHeaders().get("sec-fetch-user"));
        return templateEngine.render("recent/security/fetch-metadata",
                "apiPath", FETCH_METADATA_ECHO_API,
                "observed", observed);
    }

    public HttpResponse timeoutEcho(Parameters params) {
        long delayMs = Math.max(0L, params.getLong("delayMs", 0L));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return json(Map.of("ok", false, "error", "Interrupted"), 500);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("delayMs", delayMs);
        body.put("message", "Completed within timeout middleware constraints");
        return json(body, 200);
    }

    public HttpResponse fetchMetadataEcho(HttpRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("requestMethod", request.getRequestMethod());
        body.put("uri", request.getUri());
        body.put("secFetchSite", request.getHeaders().get("sec-fetch-site"));
        body.put("secFetchMode", request.getHeaders().get("sec-fetch-mode"));
        body.put("secFetchDest", request.getHeaders().get("sec-fetch-dest"));
        body.put("note", "If this endpoint returns 200, FetchMetadataMiddleware allowed the request.");
        return json(body, 200);
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
}
