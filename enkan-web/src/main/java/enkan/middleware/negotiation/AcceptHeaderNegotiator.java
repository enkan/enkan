package enkan.middleware.negotiation;

import enkan.util.CodecUtils;

import jakarta.ws.rs.core.MediaType;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Collections.reverseOrder;

/**
 * @author kawasima
 */
public class AcceptHeaderNegotiator implements ContentNegotiator {
    private static final Pattern ACCEPT_FRAGMENT_PARAM_RE = Pattern.compile("([^()<>@,;:\"/\\[\\]?={} 	]+)=([^()<>@,;:\"/\\[\\]?={} 	]+|\"(?:[^\"\\\\]|\\\\.)*\")$");
    private static final Pattern ACCEPTS_DELIMITER = Pattern.compile("[\\s\\n\\r]*,[\\s\\n\\r]*");
    private static final Pattern ACCEPT_DELIMITER = Pattern.compile("[\\s\\n\\r]*;[\\s\\n\\r]*");

    /** Cache: (acceptHeader + "|" + allowedTypes) → resolved MediaType */
    private final ConcurrentHashMap<String, Optional<MediaType>> contentTypeCache = new ConcurrentHashMap<>();
    /** Cache: (acceptHeader + "|" + available) → resolved charset */
    private final ConcurrentHashMap<String, Optional<String>> charsetCache = new ConcurrentHashMap<>();
    /** Cache: (acceptHeader + "|" + available) → resolved language */
    private final ConcurrentHashMap<String, Optional<String>> languageCache = new ConcurrentHashMap<>();

    // RFC 9110 §12.4.2: qvalue = ( "0" [ "." 0*3DIGIT ] ) / ( "1" [ "." 0*3("0") ] )
    private static final Pattern RE_QVALUE = Pattern.compile("0(?:\\.\\d{0,3})?|1(?:\\.0{0,3})?");

    public double parseQ(String qstr) {
        if (qstr == null || !RE_QVALUE.matcher(qstr).matches()) {
            return 0.0;
        }
        return Double.parseDouble(qstr);
    }

    public AcceptFragment<MediaType> parseMediaTypeAcceptFragment(String accept) {
        String[] tokens = ACCEPT_DELIMITER.split(accept);
        if (tokens.length > 0) {
            Optional<Double> q = Arrays.stream(tokens).skip(1)
                    .map(ACCEPT_FRAGMENT_PARAM_RE::matcher)
                    .filter(Matcher::find)
                    .filter(m -> m.group(1).equals("q"))
                    .map(m -> parseQ(m.group(2)))
                    .findFirst();
            MediaType mt = CodecUtils.parseMediaType(tokens[0]);
            return new AcceptFragment<>(mt, q.orElse(1.0), mediaTypeSpecificity(mt));
        }
        return null;
    }

    public AcceptFragment<String> parseStringAcceptFragment(String accept) {
        String[] tokens = ACCEPT_DELIMITER.split(accept);
        if (tokens.length > 0) {
            Optional<Double> q = Arrays.stream(tokens).skip(1)
                    .map(ACCEPT_FRAGMENT_PARAM_RE::matcher)
                    .filter(Matcher::find)
                    .filter(m -> m.group(1).equals("q"))
                    .map(m -> parseQ(m.group(2)))
                    .findFirst();
            return new AcceptFragment<>(tokens[0], q.orElse(1.0));
        }
        return null;
    }

    protected Function<AcceptFragment<MediaType>, AcceptFragment<MediaType>> createServerWeightFunc(Set<MediaType> allowedTypes) {
        return fragment -> {
            Optional<MediaType> matched = allowedTypes.stream()
                    .filter(mt -> fragment.fragment().isCompatible(mt))
                    .findFirst();
            return matched
                    .map(mediaType -> new AcceptFragment<>(mediaType, fragment.q(), fragment.specificity()))
                    .orElseGet(() -> new AcceptFragment<>(fragment.fragment(), 0.0, fragment.specificity()));
        };
    }

    protected Optional<String> selectBest(Set<String> candidates, Function<String, Double> scoreFunc) {
        return candidates.stream()
                .map(c -> new AcceptFragment<>(c, scoreFunc.apply(c)))
                .sorted(Comparator.comparing(AcceptFragment::q, reverseOrder()))
                .filter(af -> af.q() > 0.0)
                .map(AcceptFragment::fragment)
                .findFirst();
    }

    private static String stableCacheKey(String header, Set<String> values) {
        return header + "|" + values.stream().sorted().collect(Collectors.joining(","));
    }

    @Override
    public MediaType bestAllowedContentType(String acceptsHeader, Set<String> allowedTypes) {
        String cacheKey = stableCacheKey(acceptsHeader, allowedTypes);
        return contentTypeCache.computeIfAbsent(cacheKey, k -> {
            Function<AcceptFragment<MediaType>, AcceptFragment<MediaType>> serverWeightFunc = createServerWeightFunc(allowedTypes.stream()
                    .map(CodecUtils::parseMediaType)
                    .collect(Collectors.toSet()));
            return Arrays.stream(ACCEPTS_DELIMITER.split(acceptsHeader))
                    .map(this::parseMediaTypeAcceptFragment)
                    .filter(Objects::nonNull)
                    .map(serverWeightFunc)
                    .max(Comparator.comparing(AcceptFragment<MediaType>::q)
                            .thenComparingInt(AcceptFragment::specificity))
                    .map(af -> af.fragment);
        }).orElse(null);
    }

    @Override
    public String bestAllowedCharset(String acceptsHeader, Set<String> available) {
        String cacheKey = stableCacheKey(acceptsHeader, available);
        return charsetCache.computeIfAbsent(cacheKey, k -> {
            // Lowercase accept keys for case-insensitive matching (RFC 9110 §12.5.3)
            Map<String, Double> accepts = Arrays
                    .stream(ACCEPTS_DELIMITER.split(acceptsHeader))
                    .map(this::parseStringAcceptFragment)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            af -> af.fragment().toLowerCase(Locale.US),
                            AcceptFragment::q,
                            (a, b) -> a));
            // Pre-resolve accept entries to canonical Charset objects once,
            // skipping "*" and unrecognized names.
            Double wildcardQ = accepts.get("*");
            Map<Charset, Double> resolvedAccepts = new HashMap<>();
            for (Map.Entry<String, Double> entry : accepts.entrySet()) {
                if ("*".equals(entry.getKey())) continue;
                try {
                    resolvedAccepts.put(Charset.forName(entry.getKey()), entry.getValue());
                } catch (UnsupportedCharsetException ignored) {}
            }
            return selectBest(available, charset -> {
                charset = charset.toLowerCase(Locale.US);
                Double q = accepts.get(charset);
                if (q != null) return q;
                // Try matching by canonical charset name (handles aliases like
                // latin1, iso_8859_1, iso-8859-1, etc.)
                try {
                    Charset cs = Charset.forName(charset);
                    q = resolvedAccepts.get(cs);
                    if (q != null) return q;
                    if (wildcardQ != null) return wildcardQ;
                    // RFC 9110 §12.5.3: ISO-8859-1 gets a default quality of 1.0
                    if (cs.equals(StandardCharsets.ISO_8859_1)) return 1.0;
                } catch (UnsupportedCharsetException ignored) {
                    // Available charset not recognized by the JVM — fall through to wildcard
                }
                if (wildcardQ != null) return wildcardQ;
                return 0.0;
            });
        }).orElse(null);
    }

    @Override
    public String bestAllowedEncoding(String acceptsHeader, Set<String> available) {
        Map<String, Double> accepts = Arrays
                .stream(ACCEPTS_DELIMITER.split(acceptsHeader))
                .map(this::parseStringAcceptFragment)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        AcceptFragment::fragment,
                        AcceptFragment::q));
        available = new HashSet<>(available);
        available.add("identity");
        Double wildcardQ = accepts.get("*");
        return selectBest(available, encoding -> {
            Double q = accepts.get(encoding);
            if (q != null) return q;
            return wildcardQ != null ? wildcardQ : 0.0;
        })
                .orElseGet(() -> {
                    if (! (accepts.getOrDefault("identity", 1.0) == 0.0
                            || (accepts.getOrDefault("*", 1.0) == 0 && !accepts.containsKey("identity")))) {
                        return "identity";
                    } else {
                        return null;
                    }
                });

    }

    @Override
    public String bestAllowedLanguage(String acceptsHeader, Set<String> available) {
        String cacheKey = stableCacheKey(acceptsHeader, available);
        return languageCache.computeIfAbsent(cacheKey, k -> {
            Map<String, Double> accepts = Arrays
                    .stream(ACCEPTS_DELIMITER.split(acceptsHeader))
                    .map(this::parseStringAcceptFragment)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            AcceptFragment::fragment,
                            AcceptFragment::q));
            Function<String, Double> score = langtag -> {
                // Direction 1: truncate available tag to find a matching range
                // e.g. available "en-gb" tries "en-gb" then "en" in accepts
                for (String x = langtag; x != null; x = x.substring(0, x.lastIndexOf('-'))) {
                    Double q = accepts.get(x);
                    if (q != null) return q;
                    if (!x.contains("-")) break;
                }
                // Direction 2 (RFC 4647 §3.4): accept range is a prefix of available tag
                // e.g. accept "en" matches available "en-gb"
                double bestQ = 0.0;
                for (Map.Entry<String, Double> entry : accepts.entrySet()) {
                    String range = entry.getKey();
                    if ("*".equals(range)) continue;
                    if (langtag.startsWith(range + "-") && entry.getValue() > bestQ) {
                        bestQ = entry.getValue();
                    }
                }
                if (bestQ > 0.0) return bestQ;
                // Wildcard range "*" matches any tag with a low default weight
                Double wildcardQ = accepts.get("*");
                if (wildcardQ != null) return wildcardQ > 0 ? wildcardQ : 0.01;
                return 0.0;
            };

            return selectBest(available, score);
        }).orElse(null);
    }


    private record AcceptFragment<T>(T fragment, double q, int specificity) implements Serializable {
        AcceptFragment(T fragment, double q) {
            this(fragment, q, 0);
        }
    }

    private static int mediaTypeSpecificity(MediaType mt) {
        if (mt.isWildcardType()) return 0;           // */*
        if (mt.isWildcardSubtype()) return 1;         // text/*
        return 2;                                      // text/html
    }

}
