package kotowari.routing.factory;

import enkan.collection.OptionMap;
import kotowari.routing.Route;
import kotowari.routing.RouteBuilder;
import kotowari.routing.Routes;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Routing patterns.
 *
 * @author kawasima
 */
public class RoutePatterns {
    private final List<Route> routeList;
    private final PatternsContext context;
    private final RouteBuilder builder;
    private final Function<List<Route>, Routes> routeCompiler;

    public RoutePatterns(@Nullable String prefix, Function<List<Route>, Routes> routeCompiler) {
        this.routeCompiler = routeCompiler;

        routeList = new ArrayList<>();
        builder = new RouteBuilder();
        context = new PatternsContext(prefix, this);
    }

    RoutingCondition httpMethodCondition(@Nullable String method, String path) {
        RoutingCondition cond = new RoutingCondition(method, path);
        cond.setContext(context);
        return cond;
    }

    public RoutingCondition all(String path) {
        return httpMethodCondition(null, path);
    }

    public RoutingCondition get(String path) {
        return httpMethodCondition("GET", path);
    }

    public RoutingCondition post(String path) {
        return httpMethodCondition("POST", path);
    }

    public RoutingCondition put(String path) {
        return httpMethodCondition("PUT", path);
    }

    public RoutingCondition patch(String path) {
        return httpMethodCondition("PATCH", path);
    }

    public RoutingCondition delete(String path) {
        return httpMethodCondition("DELETE", path);
    }

    public @Nullable Route resource(Class<?> controller) {
        return resource(controller, null);
    }

    private String decapitalize(String s) {
        if (s != null && s.length() > 1) {
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        } else {
            return s;
        }
    }

    public @Nullable Route resource(Class<?> controller, @Nullable OptionMap options) {
        String name = decapitalize(controller.getSimpleName().replaceAll("Controller$", ""));
        get(name + "/").to(controller, "index");
        get(name + "/:id"     ).requires("id", "\\d+").to(controller, "show");
        get(name + "/new"     ).to(controller, "newForm");
        post(name + "/"       ).to(controller, "create");
        get(name + "/:id/edit").requires("id", "\\d+").to(controller, "edit");
        put(name + "/:id"     ).requires("id", "\\d+").to(controller, "update");
        delete(name + "/:id"   ).requires("id", "\\d+").to(controller, "delete");

        return null;
    }

    public void scope(String path, RoutePatternsDescriptor subDesc) {
        RoutePatterns subPatterns = Routes.define(context.joinPaths(path), subDesc);
        routeList.addAll(subPatterns.routeList);
    }

    private void addRoute_(Route route) {
        routeList.add(route);
    }

    private RouteBuilder getBuilder() {
        return builder;
    }


    public Routes compile() {
        return routeCompiler.apply(routeList);
    }

    public record PatternsContext(@Nullable String prefix, RoutePatterns patterns) {
        public void addRoute(Route route) {
            patterns.addRoute_(route);
        }

        String joinPaths(String path) {
            if (prefix == null) return path;
            return (prefix + "/" + path).replaceAll("//+", "/");
        }

        public Route build(String path, OptionMap options) {
            return patterns.getBuilder().build(joinPaths(path), options);
        }
    }
}
