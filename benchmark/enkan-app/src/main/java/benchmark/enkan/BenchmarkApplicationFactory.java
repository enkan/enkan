package benchmark.enkan;

import benchmark.enkan.jaxrs.JsonBodyReader;
import benchmark.enkan.jaxrs.JsonBodyWriter;
import enkan.Application;
import enkan.web.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.web.data.HttpRequest;
import enkan.web.data.HttpResponse;
import enkan.middleware.*;
import enkan.web.middleware.session.MemoryStore;
import enkan.system.inject.ComponentInjector;
import kotowari.middleware.*;
import kotowari.middleware.serdes.ToStringBodyWriter;
import kotowari.routing.Routes;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import jakarta.ws.rs.ext.MessageBodyWriter;

import java.util.Set;

import static enkan.util.BeanBuilder.builder;

public class BenchmarkApplicationFactory implements ApplicationFactory<HttpRequest, HttpResponse> {
    @Override
    public Application<HttpRequest, HttpResponse> create(ComponentInjector injector) {
        WebApplication app = new WebApplication();
        ObjectMapper mapper = JsonMapper.builder().build();

        Routes routes = Routes.define(r -> {
            r.get("/hello").to(benchmark.enkan.controller.BenchmarkController.class, "hello");
            r.get("/json").to(benchmark.enkan.controller.BenchmarkController.class, "json");
            r.get("/echo").to(benchmark.enkan.controller.BenchmarkController.class, "echo");
        }).compile();

        app.use(new DefaultCharsetMiddleware());
        app.use(new SecurityHeadersMiddleware());
        app.use(new ContentTypeMiddleware());
        app.use(new ParamsMiddleware());
        app.use(new NestedParamsMiddleware());
        app.use(new CookiesMiddleware());
        app.use(builder(new SessionMiddleware())
                .set(SessionMiddleware::setStore, new MemoryStore())
                .build());
        app.use(builder(new ContentNegotiationMiddleware())
                .set(ContentNegotiationMiddleware::setAllowedTypes,
                        Set.of("text/html", "text/plain", "application/json"))
                .build());
        app.use(new RoutingMiddleware(routes));
        app.use(builder(new SerDesMiddleware<>())
                .set(SerDesMiddleware::setBodyWriters,
                        new MessageBodyWriter[]{
                                new ToStringBodyWriter(),
                                new JsonBodyWriter<>(mapper)})
                .set(SerDesMiddleware::setBodyReaders,
                        new JsonBodyReader<>(mapper))
                .build());
        app.use(new ControllerInvokerMiddleware<>(injector));

        return app;
    }
}
