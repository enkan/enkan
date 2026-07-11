package kotowari.graalvm;

import kotowari.routing.Routes;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static registry for Routes, used by {@link KotowariFeature} to discover routes at
 * GraalVM native image build time.
 *
 * <p>Register your compiled Routes before the native image build runs analysis:
 * <pre>{@code
 * Routes routes = Routes.define(r -> { ... }).compile();
 * RouteRegistry.register(routes);
 * }</pre>
 *
 * <p>Alternatively, set the system property {@code kotowari.routes.factory} to a
 * fully-qualified class name that has a public static {@code routes()} method returning
 * {@link Routes}. {@link KotowariFeature} will invoke it reflectively at build time.
 */
public final class RouteRegistry {
    private static final AtomicReference<Routes> INSTANCE = new AtomicReference<>();

    private RouteRegistry() {}

    public static void register(Routes routes) {
        INSTANCE.set(routes);
    }

    public static @Nullable Routes get() {
        return INSTANCE.get();
    }
}
