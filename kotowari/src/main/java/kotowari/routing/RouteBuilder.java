package kotowari.routing;

import enkan.collection.OptionMap;
import kotowari.routing.segment.*;

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author kawasima
 */
public class RouteBuilder {
    public static final String[] SEPARATORS = {"/", ".", "?", "(", ")"};
    private static final Pattern PTN_OPTIONAL_FORMAT = Pattern.compile("\\A\\.(:format?)/");
    private static final Pattern PTN_SYMBOL          = Pattern.compile("\\A(?::(\\w+)|\\(:(\\w+)\\))");
    private static final Pattern PTN_PATH            = Pattern.compile("\\A\\*(\\w+)");
    private static final Pattern PTN_STATIC          = Pattern.compile("\\A\\?(.*?)\\?");
    private static final List<String> HTTP_METHODS =
            Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");


    private static final List<String> optionalSeparators = Collections.singletonList("/");
    private static final Pattern separatorRegexp = Pattern.compile("[" + RegexpUtils.escape(String.join("", SEPARATORS)) + "]");
    private static final Pattern nonseparatorRegexp = Pattern.compile("\\A([^" + RegexpUtils.escape(String.join("", SEPARATORS))+ "]+)");

    public List<Segment> segmentsForRoutePath(String path) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder rest = new StringBuilder(path);

        while (!rest.isEmpty()) {
            Segment segment = segmentFor(rest);
            segments.add(segment);
        }
        return segments;
    }

    public Segment segmentFor(StringBuilder sb) {
        String str = sb.toString();
        Segment segment = null;
        Matcher m;
        if ((m = PTN_OPTIONAL_FORMAT.matcher(str)).find()) {
            segment = new OptionalFormatSegment();
        } else if ((m = PTN_SYMBOL.matcher(str)).find()) {
            OptionMap options = OptionMap.of();
            String key = m.group(1);
            if (key.isEmpty()) {
                key = m.group(2);
                options.put("wrapParentheses", true);
            }
            segment = new DynamicSegment(key, options);
        } else if ((m = PTN_PATH.matcher(str)).find()) {
            segment = new PathSegment(m.group(1), OptionMap.of("optional", true));
        } else if ((m = PTN_STATIC.matcher(str)).find()) {
            segment = new StaticSegment(m.group(1), OptionMap.of("optional", true));
        } else if ((m = nonseparatorRegexp.matcher(str)).find()) {
            segment = new StaticSegment(m.group(1));
        } else if ((m = separatorRegexp.matcher(str)).find()) {
            segment = new DividerSegment(m.group(), OptionMap.of("optional", optionalSeparators.contains(m.group())));
        }
        if (m == null || segment == null) {
            throw new IllegalArgumentException("Cannot parse route path segment: " + sb);
        }
        sb.delete(0, m.end());
        return segment;
    }

    public OptionMap[] divideRouteOptions(List<Segment> segments, OptionMap options) {
        options.remove("pathPrefix");
        options.remove("namePrefix");

        if (options.containsKey("namespace")) {
            String namespace = Objects.requireNonNull(options.getString("namespace")).replace("/$", "");
            options.remove("namespace");
            options.put("controller", namespace.replace('/', '.') + "." + options.get("controller"));
        }


        OptionMap requirements = Optional.ofNullable((OptionMap)options.remove("requirements")).orElse(OptionMap.of());
        OptionMap defaults = Optional.ofNullable((OptionMap)options.remove("defaults")).orElse(OptionMap.of());
        OptionMap conditions = Optional.ofNullable((OptionMap)options.remove("conditions")).orElse(OptionMap.of());

        validateRouteConditions(conditions);

        List<String> pathKeys = segments.stream()
                .filter(Segment::hasKey)
                .map(Segment::getKey)
                .toList();

        for (Map.Entry<String, Object> e : options.entrySet()) {
            if (pathKeys.contains(e.getKey()) && !(e.getValue() instanceof Pattern)) {
                defaults.put(e.getKey(), e.getValue());
            } else {
                requirements.put(e.getKey(), e.getValue());
            }
        }

        return new OptionMap[]{ defaults, requirements, conditions };
    }

    private @Nullable Segment findSegment(List<Segment> segments, String key) {
        for (Segment seg : segments) {
            if (seg.hasKey() && key.equals(seg.getKey())) {
                return seg;
            }
        }
        return null;
    }
    public OptionMap assignRouteOptions(List<Segment> segments, OptionMap defaults, OptionMap requirements) {
        OptionMap routeRequirements = OptionMap.of();

        for (Map.Entry<String, Object> e : requirements.entrySet()) {
            final String key = e.getKey();
            final Object requirement = e.getValue();
            Segment segment = findSegment(segments, key);
            if (segment != null) {
                segment.setRegexp((Pattern)requirement);
            } else {
                routeRequirements.put(key, requirement);
            }
        }

        for (String key : defaults.keySet()) {
            final String defaultValue = defaults.getString(key);
            Segment segment = findSegment(segments, key);
            if (segment == null)
                throw new IllegalArgumentException(key + ": No matching segment exists; cannot assign default");
            if (defaultValue != null)
                segment.setOptional(true);
            segment.setDefault(defaultValue);
        }

        assignDefaultRouteOptions(segments);
        ensureRequiredSegments(segments);
        return routeRequirements;
    }

    private void assignDefaultRouteOptions(List<Segment> segments) {
        for (Segment segment : segments) {
            if (!(segment instanceof DynamicSegment))
                continue;
            String key = segment.getKey();
            if ("action".equals(key)) {
                segment.setDefault("index");
                segment.setOptional(true);
            } else if ("id".equals(key)) {
                segment.setOptional(true);
            }
        }
    }

    private void ensureRequiredSegments(List<Segment> segments) {
        boolean allowOptional = true;
        for (int i=segments.size() - 1; i >= 0; i--) {
            Segment segment = segments.get(i);
            allowOptional = allowOptional && segment.isOptional();
            if (!allowOptional && segment.isOptional()) {
                segment.setOptional(false);
            } else if (allowOptional && segment.hasDefault() && segment.getDefault() != null && segment.getDefault().isEmpty()) {
                segment.setOptional(true);
            }
        }
    }

    public Route build(String path, OptionMap options) {
        if (path.charAt(0) != '/')
            path = "/" + path;

        String prefix = options.getString("pathPrefix");
        if (prefix != null && !prefix.isEmpty()) {
            path = "/" + prefix.replace("^/", "") + path;
        }

        List<Segment> segments = segmentsForRoutePath(path);
        OptionMap[] extOptions = divideRouteOptions(segments, options);
        OptionMap requirements = assignRouteOptions(segments, extOptions[0]/*defaults*/ , extOptions[1]/*requirements*/);

        return new Route(segments, requirements, extOptions[2]/*conditions*/);
    }

    private void validateRouteConditions(OptionMap conditions) {
        List<Object> methods = conditions.getList("method");

        for (Object m : methods) {
            String method = (String)m;
            if ("HEAD".equals(method)) {
                throw new IllegalArgumentException("HTTP method HEAD is invalid in route conditions.");
            }

            if (!HTTP_METHODS.contains(method)) {
                throw new IllegalArgumentException("Invalid HTTP method specified in route conditions: " + conditions);
            }
        }
    }
}
