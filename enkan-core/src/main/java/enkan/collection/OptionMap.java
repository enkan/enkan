package enkan.collection;

import java.util.*;

/**
 * A string-keyed map with typed accessor methods for configuration options.
 *
 * <p>Provides convenience getters that coerce values to common types
 * ({@code String}, {@code int}, {@code long}, {@code boolean}, {@code List}).
 * Null values fall back to caller-supplied defaults.</p>
 *
 * @author kawasima
 */
public class OptionMap extends HashMap<String, Object> {

    /**
     * Returns a new empty {@code OptionMap}.
     *
     * @return an empty option map
     */
    public static OptionMap empty() {
        return new OptionMap();
    }

    /**
     * Returns a shallow copy of the given option map.
     *
     * @param init the source map to copy
     * @return a new option map containing all entries from {@code init}
     */
    public static OptionMap of(OptionMap init) {
        OptionMap m = empty();
        m.putAll(init);
        return m;
    }

    /**
     * Creates an option map from alternating key-value pairs.
     *
     * @param init alternating {@code (String key, Object value)} pairs
     * @return a new option map
     * @throws enkan.exception.MisconfigurationException if the array length is odd
     */
    public static OptionMap of(Object... init) {
        if (init.length % 2 != 0) {
            throw new enkan.exception.MisconfigurationException("core.MISSING_KEY_VALUE_PAIR");
        }
        OptionMap m = empty();
        for(int i = 0; i < init.length; i += 2) {
            m.put(init[i].toString(), init[i + 1]);
        }
        return m;
    }

    /**
     * Returns the value as a {@code String}, or {@code null} if absent.
     *
     * @param key the key
     * @return the string value, or {@code null}
     */
    public String getString(String key) {
        return getString(key, null);
    }

    /**
     * Returns the value as a {@code String}, or the default if absent.
     *
     * @param key          the key
     * @param defaultValue the fallback value
     * @return the string value, or {@code defaultValue}
     */
    public String getString(String key, String defaultValue) {
        Object value = get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }

    /**
     * Returns the value as an {@code int}, or {@code 0} if absent.
     *
     * @param key the key
     * @return the int value, or {@code 0}
     */
    public int getInt(String key) {
        return getInt(key, 0);
    }

    /**
     * Returns the value as an {@code int}, or the default if absent.
     * {@link Number} values are converted via {@code intValue()};
     * other types are parsed via {@link Integer#parseInt(String)}.
     *
     * @param key          the key
     * @param defaultValue the fallback value
     * @return the int value, or {@code defaultValue}
     */
    public int getInt(String key, int defaultValue) {
        Object value = get(key);
        if (value == null) return defaultValue;

        if (value instanceof Number n) {
            return n.intValue();
        } else {
            return Integer.parseInt(value.toString());
        }
    }

    /**
     * Returns the value as a {@code long}, or {@code 0L} if absent.
     *
     * @param key the key
     * @return the long value, or {@code 0L}
     */
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    /**
     * Returns the value as a {@code long}, or the default if absent.
     *
     * @param key          the key
     * @param defaultValue the fallback value
     * @return the long value, or {@code defaultValue}
     */
    public long getLong(String key, long defaultValue) {
        Object value = get(key);
        if (value == null) return defaultValue;

        if (value instanceof Number n) {
            return n.longValue();
        } else {
            return Long.parseLong(value.toString());
        }
    }

    /**
     * Returns the value as a {@code boolean}, or {@code true} if absent.
     *
     * @param key the key
     * @return the boolean value, or {@code true}
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, true);
    }

    /**
     * Returns the value as a {@code boolean}, or the default if absent.
     * {@link Boolean} values are returned directly; other types are
     * coerced via {@code getInt(key) != 0}.
     *
     * @param key          the key
     * @param defaultValue the fallback value
     * @return the boolean value, or {@code defaultValue}
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = get(key);
        if (value == null) return defaultValue;

        if (value instanceof Boolean b) {
            return b;
        } else {
            return getInt(key) != 0;
        }
    }

    /**
     * Returns the value as a {@code List}. If the value is already a
     * {@code List}, it is returned as-is. Arrays and other collections
     * are wrapped. A scalar value is wrapped in a single-element list.
     * Returns an empty list if the key is absent.
     *
     * @param key the key
     * @return a list view of the value, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object value = this.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        List<Object> valueList;
        if (List.class.isAssignableFrom(value.getClass())) {
            valueList = (List<Object>) value;
        } else if (value.getClass().isArray()) {
            valueList = Arrays.asList((Object[])value);
        } else if (Collection.class.isAssignableFrom(value.getClass())) {
            valueList = new ArrayList<>((Collection<Object>) value);
        } else {
            valueList = new ArrayList<>(1);
            valueList.add(value);
        }
        return valueList;
    }
}
