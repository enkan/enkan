package kotowari.example.graalvm;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import enkan.Application;
import enkan.application.WebApplication;
import enkan.config.ApplicationFactory;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.middleware.*;
import enkan.system.inject.ComponentInjector;
import jakarta.ws.rs.ext.MessageBodyWriter;
import kotowari.example.graalvm.controller.SessionController;
import kotowari.example.graalvm.controller.TodoController;
import kotowari.example.graalvm.jaxrs.JsonBodyReader;
import kotowari.example.graalvm.jaxrs.JsonBodyWriter;
import kotowari.graalvm.NativeControllerInvokerMiddleware;
import kotowari.graalvm.RouteRegistry;
import kotowari.middleware.*;
import kotowari.middleware.serdes.ToStringBodyWriter;
import kotowari.routing.Routes;

import java.util.Set;

import static enkan.util.BeanBuilder.builder;

public class NativeApplicationFactory implements ApplicationFactory<HttpRequest, HttpResponse> {

    /**
     * Returns the compiled routes for this application.
     * Called by {@code KotowariFeature} at native-image build time via the
     * {@code kotowari.routes.factory} system property.
     */
    public static Routes routes() {
        return Routes.define(r -> {
            r.get("/todos").to(TodoController.class, "list");
            r.get("/todos/:id").to(TodoController.class, "show");
            r.post("/todos").to(TodoController.class, "create");
            r.post("/todos/validate").to(TodoController.class, "createWithValidation");
            r.get("/session").to(SessionController.class, "visit");
        }).compile();
    }

    /**
     * Builds the full {@link WebApplication} middleware stack.
     *
     * <p>Called by {@code KotowariFeature} at native-image build time (with
     * {@code injector = null}) to trigger mixin class pre-generation via
     * {@link enkan.util.MixinUtils#createFactory}. At runtime it is invoked
     * by {@link #create} with the real {@link ComponentInjector}.
     *
     * @param injector the component injector, or {@code null} at build time
     */
    public static WebApplication buildApp(ComponentInjector injector) {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        Routes routes = routes();

        if (injector != null) {
            // Register routes in the runtime registry when a real injector is available.
            RouteRegistry.register(routes);
        }

        WebApplication app = new WebApplication();
        app.use(new DefaultCharsetMiddleware());
        app.use(new ContentTypeMiddleware());
        app.use(new ParamsMiddleware());
        app.use(new CookiesMiddleware());
        app.use(new SessionMiddleware());
        app.use(builder(new ContentNegotiationMiddleware())
                .set(ContentNegotiationMiddleware::setAllowedTypes, Set.of("application/json"))
                .build());
        app.use(new ResourceMiddleware());
        app.use(new RoutingMiddleware(routes));
        app.use(builder(new SerDesMiddleware<>())
                .set(SerDesMiddleware::setBodyWriters,
                        new MessageBodyWriter[]{
                                new ToStringBodyWriter(),
                                new JsonBodyWriter<>(mapper)})
                .set(SerDesMiddleware::setBodyReaders,
                        new JsonBodyReader<>(mapper))
                .build());
        app.use(new ValidateBodyMiddleware<>());
        app.use(new NativeControllerInvokerMiddleware<>(injector));

        return app;
    }

    /**
     * Build-time entry point called by {@code KotowariFeature} and
     * {@code GenerateMixinConfig} to trigger mixin class pre-generation.
     * Delegates to {@link #buildApp(ComponentInjector)} with a {@code null}
     * injector.
     */
    public static WebApplication buildApp() {
        return buildApp(null);
    }

    @Override
    public Application<HttpRequest, HttpResponse> create(ComponentInjector injector) {
        return buildApp(injector);
    }
}
