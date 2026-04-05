package kotowari.example;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import enkan.Application;
import enkan.Endpoint;
import enkan.web.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.web.endpoint.ResourceEndpoint;
import enkan.middleware.*;
import enkan.web.middleware.*;
import enkan.middleware.doma2.DomaTransactionMiddleware;
import enkan.middleware.opentelemetry.TracingMiddleware;
import enkan.web.middleware.session.MemoryStore;
import enkan.predicate.PathPredicate;
import enkan.security.crypto.CryptoAlgorithm;
import enkan.security.crypto.JcaVerifier;
import enkan.web.security.backend.SessionBackend;
import enkan.web.signature.SignatureAlgorithm;
import enkan.web.signature.SignatureComponent;
import enkan.web.signature.SignatureKeyResolver;
import enkan.system.inject.ComponentInjector;
import enkan.web.util.HttpResponseUtils;
import kotowari.example.controller.*;
import kotowari.example.controller.api.HttpIntegrityDemoController;
import kotowari.example.controller.api.RecentFeaturesDemoController;
import kotowari.example.controller.api.RecentSecurityDemoController;
import kotowari.example.controller.api.SseController;
import kotowari.example.controller.api.TodoApiController;
import kotowari.example.controller.guestbook.GuestbookController;
import kotowari.example.controller.guestbook.LoginController;
import kotowari.example.jaxrs.JsonBodyReader;
import kotowari.example.jaxrs.JsonBodyWriter;
import kotowari.example.middleware.RequestTimeoutDemoMiddleware;
import kotowari.middleware.*;
import kotowari.middleware.serdes.ToStringBodyWriter;
import kotowari.routing.Routes;
import jakarta.ws.rs.ext.MessageBodyWriter;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static enkan.util.BeanBuilder.*;
import static enkan.util.Predicates.*;

/**
 * @author kawasima
 */
public class ExampleApplicationFactory implements ApplicationFactory<HttpRequest, HttpResponse> {
    @Override
    public Application<HttpRequest, HttpResponse> create(ComponentInjector injector) {
        WebApplication app = new WebApplication();
        ObjectMapper mapper = JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        // Routing
        Routes routes = Routes.define(r -> {
            r.get("/").to(ExampleController.class, "index");
            r.get("/m2").to(ExampleController.class, "method2");
            r.get("/m3").to(ExampleController.class, "method3");
            r.get("/m4").to(ExampleController.class, "method4");

            r.get("/guestbook/login").to(LoginController.class, "loginForm");
            r.post("/guestbook/login").to(LoginController.class, "login");
            r.get("/guestbook/").to(GuestbookController.class, "list");
            r.post("/guestbook/").to(GuestbookController.class, "post");

            r.get("/conversation/1").to(ConversationStateController.class, "page1");
            r.post("/conversation/2").to(ConversationStateController.class, "page2");
            r.post("/conversation/3").to(ConversationStateController.class, "page3");
            r.get("/misc/counter").to(MiscController.class, "counter");
            r.get("/misc/upload").to(MiscController.class, "uploadForm");
            r.post("/misc/upload").to(MiscController.class, "upload");
            r.get("/hospitality/unreachable").to(HospitalityDemoController.class, "unreachable");
            r.get("/hospitality/misconfiguration").to(HospitalityDemoController.class, "misconfiguration");
            r.resource(CustomerController.class);
            r.get("/customer/list").to(CustomerController.class, "list");
            r.post("/customer/validate").to(CustomerController.class, "validate");

            // SSE API
            r.get("/api/sse/countdown").to(SseController.class, "countdown");
            r.get("/api/sse/tick").to(SseController.class, "tick");

            // JSON API
            r.get("/api/todo").to(TodoApiController.class, "list");
            r.get("/api/todo/:id").to(TodoApiController.class, "show");
            r.post("/api/todo").to(TodoApiController.class, "create");
            r.put("/api/todo/:id").to(TodoApiController.class, "update");
            r.delete("/api/todo/:id").to(TodoApiController.class, "delete");

            // RFC 9530 + RFC 9421 API
            r.get("/api/http-integrity/sample").to(HttpIntegrityDemoController.class, "sample");
            r.post("/api/http-integrity/verify").to(HttpIntegrityDemoController.class, "verify");

            // Recent features demos
            r.get("/api/recent/idempotency/sample").to(RecentFeaturesDemoController.class, "idempotencySample");
            r.post("/api/recent/idempotency/echo").to(RecentFeaturesDemoController.class, "idempotencyEcho");
            r.get("/api/recent/jwt/issue").to(RecentFeaturesDemoController.class, "jwtIssue");
            r.get("/api/recent/jwt/verify").to(RecentFeaturesDemoController.class, "jwtVerify");

            // Recent feature demos (HTML)
            r.get("/recent/jwt/demo").to(RecentFeaturesDemoController.class, "jwtDemoPage");
            r.get("/recent/idempotency/demo").to(RecentFeaturesDemoController.class, "idempotencyDemoPage");
            r.get("/recent/sse/demo").to(RecentFeaturesDemoController.class, "sseDemoPage");
            r.get("/recent/http-integrity/demo").to(HttpIntegrityDemoController.class, "demoPage");

            // Recent security/runtime demos (HTML)
            r.get("/recent/security/csp-nonce").to(RecentSecurityDemoController.class, "cspNoncePage");
            r.get("/recent/security/request-timeout").to(RecentSecurityDemoController.class, "requestTimeoutPage");
            r.get("/recent/security/fetch-metadata").to(RecentSecurityDemoController.class, "fetchMetadataPage");

            // Recent security/runtime demos (JSON API)
            r.get("/api/recent/security/timeout-echo").to(RecentSecurityDemoController.class, "timeoutEcho");
            r.get("/api/recent/security/fetch-metadata-echo").to(RecentSecurityDemoController.class, "fetchMetadataEcho");
        }).compile();

        var demoKey = new SecretKeySpec(
                System.getenv().getOrDefault("HTTP_INTEGRITY_DEMO_SECRET", "kotowari-demo-shared-secret")
                        .getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        SignatureKeyResolver demoResolver = new SignatureKeyResolver() {
            @Override
            public Optional<enkan.security.crypto.Verifier> resolveVerifier(String keyId, SignatureAlgorithm algorithm) {
                if (HttpIntegrityDemoController.KEY_ID.equals(keyId)
                        && algorithm == SignatureAlgorithm.HMAC_SHA256) {
                    return Optional.of(new JcaVerifier(CryptoAlgorithm.HMAC_SHA256, demoKey));
                }
                return Optional.empty();
            }

            @Override
            public Optional<enkan.security.crypto.Signer> resolveSigner(String keyId, SignatureAlgorithm algorithm) {
                return Optional.empty();
            }
        };
        SignatureVerificationMiddleware integritySignature = new SignatureVerificationMiddleware(demoResolver);
        integritySignature.setRequiredLabels(Set.of("sig1"));
        integritySignature.setRequiredComponents(Set.of("@method", "@path", "content-digest"));
        integritySignature.setAcceptSignature(
                "sig1",
                List.of(
                        SignatureComponent.of("@method"),
                        SignatureComponent.of("@path"),
                        SignatureComponent.of("content-digest")
                ),
                SignatureAlgorithm.HMAC_SHA256,
                HttpIntegrityDemoController.KEY_ID
        );

        MemoryStore idempotencyStore = new MemoryStore();
        idempotencyStore.setTtlSeconds(24 * 60 * 60L);
        IdempotencyKeyMiddleware idempotencyDemo = new IdempotencyKeyMiddleware();
        idempotencyDemo.setStore(idempotencyStore);
        idempotencyDemo.setMethods(Set.of("POST"));
        RequestTimeoutDemoMiddleware recentTimeout = new RequestTimeoutDemoMiddleware(200);
        FetchMetadataMiddleware recentFetchMetadata = new FetchMetadataMiddleware();

        // Enkan
        app.use(new DefaultCharsetMiddleware());
        app.use(path("^/recent/security/csp-nonce$"), new CspNonceMiddleware());
        app.use(builder(new SecurityHeadersMiddleware())
                .set(SecurityHeadersMiddleware::setContentSecurityPolicy,
                        "default-src 'self' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; connect-src 'self' ws://localhost:* http://localhost:*")
                .build());
        app.use(new TracingMiddleware());
        app.use(none(), new ServiceUnavailableMiddleware<>(new ResourceEndpoint("/public/html/503.html")));
        app.use(envIn("development"), new LazyLoadMiddleware<>("enkan.middleware.devel.StacktraceMiddleware"));
        app.use(envIn("development"), new LazyLoadMiddleware<>("enkan.middleware.devel.TraceWebMiddleware"));
        app.use(new TraceMiddleware<>());
        app.use(new ContentTypeMiddleware());
        app.use(envIn("development"), new LazyLoadMiddleware<>("enkan.middleware.devel.HttpStatusCatMiddleware"));
        app.use(new ParamsMiddleware());
        app.use(new MultipartParamsMiddleware());
        app.use(new MethodOverrideMiddleware());
        app.use(new NormalizationMiddleware());
        app.use(new NestedParamsMiddleware());
        app.use(new CookiesMiddleware());
        // Dev endpoints: must be before SessionMiddleware to avoid NPE on null response
        app.use(PathPredicate.ANY("^/x-enkan/repl/.*"),
                new LazyLoadMiddleware<>("enkan.endpoint.devel.ReplConsoleEndpoint"));
        app.use(path("^/x-enkan/dev-info$"),
                new LazyLoadMiddleware<>("enkan.endpoint.devel.DevInfoEndpoint"));
        app.use(path("^/api/http-integrity/verify$"), new DigestValidationMiddleware());
        app.use(path("^/api/http-integrity/verify$"), integritySignature);
        app.use(path("^/api/recent/idempotency/.*$"), idempotencyDemo);
        app.use(path("^/api/recent/security/timeout-echo$"), recentTimeout);
        app.use(path("^/api/recent/security/fetch-metadata-echo$"), recentFetchMetadata);

        app.use(builder(new SessionMiddleware())
                .set(SessionMiddleware::setStore, new MemoryStore())
                .build());
        app.use(PathPredicate.ANY("^/(guestbook|conversation)/.*"), new ConversationMiddleware());

        app.use(new AuthenticationMiddleware<>(Collections.singletonList(new SessionBackend())));
        app.use(and(path("^/guestbook/"), authenticated().negate()),
                (Endpoint<HttpRequest , HttpResponse>)req ->
                        HttpResponseUtils.redirect("/guestbook/login?url=" + req.getUri(),
                                HttpResponseUtils.RedirectStatusCode.TEMPORARY_REDIRECT));
        app.use(new ContentNegotiationMiddleware());
        app.use(new ResourceMiddleware());
        app.use(new RenderTemplateMiddleware());
        app.use(new RoutingMiddleware(routes));
        app.use(new DomaTransactionMiddleware<>());
        app.use(new FormMiddleware());
        app.use(builder(new SerDesMiddleware<>())
                .set(SerDesMiddleware::setBodyWriters,
                        new MessageBodyWriter[]{
                                new ToStringBodyWriter(),
                                new JsonBodyWriter<>(mapper)})
                .set(SerDesMiddleware::setBodyReaders,
                        new JsonBodyReader<>(mapper))
                .build());
        app.use(new ValidateBodyMiddleware<>());
        app.use(new ControllerInvokerMiddleware<>(injector));

        return app;
    }
}
