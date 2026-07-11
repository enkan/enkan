package kotowari.routing.factory;

import enkan.collection.OptionMap;
import kotowari.routing.Route;

import org.jspecify.annotations.Nullable;

import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Representation for conditions of routing.
 *
 * @author kawasima
 */
public class RoutingCondition {
    private final @Nullable String method;
    private final String path;
    private final OptionMap requirements;
    private Set<MediaType> consumes;
    private Set<MediaType> produces;
    private RoutePatterns.@Nullable PatternsContext context;

    public RoutingCondition(@Nullable String method, String path) {
        this.method = method;
        this.path = path;
        this.requirements = OptionMap.empty();
        this.consumes = Collections.emptySet();
        this.produces = Collections.emptySet();
    }

    public void setContext(RoutePatterns.PatternsContext context) {
        this.context = context;
    }

    public RoutingCondition requires(String patternVariable, String pattern) {
        requirements.put(patternVariable, Pattern.compile(pattern));
        return this;
    }

    public RoutingCondition consumes(MediaType... mediaTypes) {
        this.consumes = Arrays.stream(mediaTypes)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return this;
    }

    public RoutingCondition consumes(String... mediaTypes) {
        this.consumes = Arrays.stream(mediaTypes)
                .filter(Objects::nonNull)
                .map(MediaType::valueOf)
                .collect(Collectors.toSet());
        return this;
    }

    public RoutingCondition produces(MediaType... mediaTypes) {
        this.produces = Arrays.stream(mediaTypes)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return this;
    }

    public RoutingCondition produces(String... mediaTypes) {
        this.produces = Arrays.stream(mediaTypes)
                .filter(Objects::nonNull)
                .map(MediaType::valueOf)
                .collect(Collectors.toSet());
        return this;
    }

    /**
     * Generate a routing definition from the given condition and destination
     */
    public Route to(Class<?> controllerClass) {
        return to(controllerClass, null);
    }

    /**
     * Generate a routing definition from the given condition and destination
     *
     * @param controllerClass  The controller of destination
     * @param controllerMethod The method of destination
     * @return a routing definition
     */
    public Route to(Class<?> controllerClass, @Nullable String controllerMethod) {
        OptionMap conditions = OptionMap.empty();
        if (method != null) {
            conditions.put("method", method);
        }
        if (!consumes.isEmpty()) {
            conditions.put("consumes", consumes);
        }
        if (!produces.isEmpty()) {
            conditions.put("produces", produces);
        }

        OptionMap options = OptionMap.of(
                "controller", controllerClass.getName(),
                "action", controllerMethod,
                "conditions", conditions);

        if (!requirements.isEmpty()) {
            options.put("requirements", requirements);
        }

        RoutePatterns.PatternsContext ctx = Objects.requireNonNull(context, "routing context");
        Route route = ctx.build(path, options);
        ctx.addRoute(route);
        return route;
    }
}
