package enkan.web.collection;

import enkan.collection.Parameters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Case-insensitive HTTP header map (RFC 7230).
 *
 * <p>Keys are stored in ASCII lowercase form. The {@link #keySet()} and
 * {@link #forEachHeader(BiConsumer)} methods return capitalized header names
 * (e.g. {@code Content-Type}) suitable for writing into HTTP responses.
 *
 * @author kawasima
 */
public class Headers extends Parameters {
    private static final Map<String, String> KEYWORDS = Stream.of("CSP", "ATT", "WAP", "IP", "HTTP", "CPU", "DNT", "SSL", "UA", "TE", "WWW", "XSS", "MD5")
            .collect(Collectors.toMap(k -> k, k -> k));

    private static final Pattern HYPHEN = Pattern.compile("-");
    private static final ConcurrentHashMap<String, String> CAPITALIZE_CACHE = new ConcurrentHashMap<>();

    private static String capitalizeHeaderName(String key) {
        return CAPITALIZE_CACHE.computeIfAbsent(key, k ->
            Arrays.stream(HYPHEN.split(k))
                .map(t -> {
                    if (t.length() < 2) {
                        return t.toUpperCase(Locale.US);
                    } else {
                        return Optional.ofNullable(KEYWORDS.get(t.toUpperCase(Locale.US)))
                                .orElseGet(() -> Character.toUpperCase(t.charAt(0)) + t.substring(1));
                    }
                })
                .collect(Collectors.joining("-"))
        );
    }

    private transient Set<String> cachedKeySet;

    protected Headers() {
        setCaseSensitive(false);
    }

    private void invalidateKeySetCache() {
        cachedKeySet = null;
    }

    /**
     * Creates an empty {@code Headers} instance.
     *
     * @return a new empty headers map
     */
    public static Headers empty() {
        return new Headers();
    }

    /**
     * Creates a {@code Headers} instance containing one header.
     *
     * @param k1 the header name
     * @param v1 the header value
     * @return a new headers map with the given entry
     */
    public static Headers of(String k1, Object v1) {
        Headers headers = empty();
        headers.put(k1, v1);
        return headers;
    }

    /**
     * Creates a {@code Headers} instance containing two headers.
     *
     * @param k1 the first header name
     * @param v1 the first header value
     * @param k2 the second header name
     * @param v2 the second header value
     * @return a new headers map with the given entries
     */
    public static Headers of(String k1, Object v1, String k2, Object v2) {
        Headers headers = Headers.of(k1, v1);
        headers.put(k2, v2);
        return headers;
    }

    /**
     * Creates a {@code Headers} instance containing three headers.
     *
     * @param k1 the first header name
     * @param v1 the first header value
     * @param k2 the second header name
     * @param v2 the second header value
     * @param k3 the third header name
     * @param v3 the third header value
     * @return a new headers map with the given entries
     */
    public static Headers of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Headers headers = Headers.of(k1, v1, k2, v2);
        headers.put(k3, v3);
        return headers;
    }

    /**
     * Creates a {@code Headers} instance containing four headers.
     *
     * @param k1 the first header name
     * @param v1 the first header value
     * @param k2 the second header name
     * @param v2 the second header value
     * @param k3 the third header name
     * @param v3 the third header value
     * @param k4 the fourth header name
     * @param v4 the fourth header value
     * @return a new headers map with the given entries
     */
    public static Headers of(String k1, Object v1, String k2, Object v2, String k3, Object v3,  String k4, Object v4) {
        Headers headers = Headers.of(k1, v1, k2, v2, k3, v3);
        headers.put(k4, v4);
        return headers;
    }

    @Override
    public Object put(String key, Object value) {
        invalidateKeySetCache();
        return super.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        invalidateKeySetCache();
        return super.remove(key);
    }

    @Override
    public Object replace(String key, Object value) {
        invalidateKeySetCache();
        return super.replace(key, value);
    }

    @Override
    public void clear() {
        invalidateKeySetCache();
        super.clear();
    }

    /**
     * Iterates over all header entries, passing the capitalized header name
     * and raw value to the consumer. Avoids the double-lookup overhead of
     * {@code keySet()} + {@code getList()}.
     */
    public void forEachHeader(BiConsumer<String, Object> consumer) {
        for (Entry<String, Object> entry : super.entrySet()) {
            String capitalizedName = capitalizeHeaderName(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                for (Object v : list) {
                    consumer.accept(capitalizedName, v);
                }
            } else {
                consumer.accept(capitalizedName, value);
            }
        }
    }

    /**
     * Returns a set of capitalized header names (e.g. {@code Content-Type}).
     * The returned set is cached and unmodifiable.
     *
     * @return an unmodifiable set of capitalized header names
     */
    @Override
    public Set<String> keySet() {
        Set<String> cached = cachedKeySet;
        if (cached != null) {
            return cached;
        }
        Set<String> keys = super.keySet();
        Set<String> headerKeys = new LinkedHashSet<>(keys.size() * 2);
        for (String key : keys) {
            headerKeys.add(capitalizeHeaderName(key));
        }
        cached = Collections.unmodifiableSet(headerKeys);
        cachedKeySet = cached;
        return cached;
    }

}
