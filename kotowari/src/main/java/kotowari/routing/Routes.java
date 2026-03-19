package kotowari.routing;

import enkan.collection.OptionMap;
import enkan.data.HttpRequest;
import enkan.exception.MisconfigurationException;
import kotowari.routing.factory.RoutePatterns;
import kotowari.routing.factory.RoutePatternsDescriptor;
import kotowari.routing.recognizer.OptimizedRecognizer;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static enkan.util.SearchUtils.levenshteinDistance;

/**
 * A compiled set of routes for URL recognition and generation.
 *
 * <p>Use {@link #define(RoutePatternsDescriptor)} to build a route table,
 * then {@code .compile()} to obtain a {@code Routes} instance.</p>
 *
 * @author kawasima
 */
public class Routes {
    private Recognizer recognizer;
    private final List<Route> routeList;

    private Routes(List<Route> routeList) {
        this.routeList = routeList;
        recognizer = new OptimizedRecognizer();
    }

    /**
     * Defines routes using the given descriptor.
     *
     * @param descriptor the route pattern descriptor
     * @return a {@link RoutePatterns} builder that can be compiled
     */
    public static RoutePatterns define(RoutePatternsDescriptor descriptor) {
        return define(null, descriptor);
    }
    public static RoutePatterns define(String prefix, RoutePatternsDescriptor descriptor) {
        RoutePatterns patterns = new RoutePatterns(prefix, routeList -> {
            Routes routes = new Routes(routeList);
            routes.recognizer.setRoutes(routeList);
            routes.recognizer.optimize();
            return routes;
        });
        descriptor.describe(patterns);
        return patterns;
    }

    /**
     * Merges routes from another {@code Routes} instance into this one.
     *
     * @param another the routes to merge
     */
    public void concat(Routes another) {
        routeList.addAll(another.routeList);
        recognizer = new OptimizedRecognizer();
        recognizer.setRoutes(routeList);
        recognizer.optimize();
    }

    /**
     * Recognizes a route from the given request.
     *
     * @param request the HTTP request
     * @return an option map with the matched route parameters, or empty if no match
     */
    public OptionMap recognizePath(HttpRequest request) {
        return recognizer.recognize(request);
    }

    /**
     * Returns the list of compiled routes.
     *
     * @return the list of routes
     */
    public List<Route> getRouteList() {
        return routeList;
    }

    /**
     * Generate a path string from route parameters.
     *
     * @param options route parameters
     * @return the path string
     */
    public String generate(OptionMap options) {
        OptionMap merged = OptionMap.of(options);
        Object controllerValue = options.get("controller");
        Class<?> controller;
        if (controllerValue instanceof Class<?> c) {
            controller = c;
            // Route.generationRequirements compares against the String FQN stored in constraints,
            // so merged must carry the class name, not the Class<?> object.
            merged.put("controller", c.getName());
        } else if (controllerValue instanceof String name) {
            try {
                controller = Class.forName(name, false,
                        Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new MisconfigurationException("core.CLASS_NOT_FOUND", name, e);
            }
        } else {
            throw new MisconfigurationException("core.INVALID_ARGUMENT",
                    "controller", controllerValue != null ? controllerValue : "null");
        }
        String action = options.getString("action");

        if (action == null) {
            throw new MisconfigurationException("core.INVALID_ARGUMENT", "action", "null");
        }
        return routeList.stream()
                .filter(r -> r.matchesControllerAndAction(controller, action))
                .filter(r -> r.significantKeys().stream().allMatch(options::containsKey))
                .map(r -> r.generate(options, merged))
                .findFirst()
                .orElseThrow(() -> new MisconfigurationException("kotowari.ROUTING_GENERATION",
                        controller, action,
                        routeList.stream()
                                .filter(r -> r.matchesController(controller))
                                .map(Object::toString)
                                .collect(Collectors.joining("")),
                        routeList.stream()
                                .filter(r -> r.matchesController(controller))
                                .map(Route::getActionRequirement)
                                .min(Comparator.comparingInt(a -> levenshteinDistance(a, action)))
                                .orElse("")
                ));

    }



    @Override
    public String toString() {
        if (routeList == null) {
            return "[empty]";
        }
        StringBuilder out = new StringBuilder(1024);
        for (Route route : routeList) {
            out.append(route.toString());
        }
        return out.toString();
    }
}
