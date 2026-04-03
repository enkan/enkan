package kotowari.example;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import enkan.Application;
import enkan.Endpoint;
import enkan.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.endpoint.ResourceEndpoint;
import enkan.middleware.*;
import enkan.middleware.doma2.DomaTransactionMiddleware;
import enkan.middleware.opentelemetry.TracingMiddleware;
import enkan.middleware.session.MemoryStore;
import enkan.predicate.PathPredicate;
import enkan.security.backend.SessionBackend;
import enkan.system.inject.ComponentInjector;
import enkan.util.HttpResponseUtils;
import kotowari.example.controller.*;
import kotowari.example.controller.api.SseController;
import kotowari.example.controller.api.TodoApiController;
import kotowari.example.controller.guestbook.GuestbookController;
import kotowari.example.controller.guestbook.LoginController;
import kotowari.example.jaxrs.JsonBodyReader;
import kotowari.example.jaxrs.JsonBodyWriter;
import kotowari.middleware.*;
import kotowari.middleware.serdes.ToStringBodyWriter;
import kotowari.routing.Routes;
import jakarta.ws.rs.ext.MessageBodyWriter;

import java.util.Collections;

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
        }).compile();

        // Enkan
        app.use(new DefaultCharsetMiddleware());
        app.use(builder(new SecurityHeadersMiddleware())
                .set(SecurityHeadersMiddleware::setContentSecurityPolicy,
                        "default-src 'self' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; connect-src 'self' ws://localhost:*")
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
        // Kotowari
        app.use(PathPredicate.ANY("^/x-enkan/repl/.*"),
                new LazyLoadMiddleware<>("enkan.endpoint.devel.ReplConsoleEndpoint"));
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
