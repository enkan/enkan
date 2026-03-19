package enkan.collection;

import enkan.exception.MisconfigurationException;

import java.io.Serializable;
import java.util.*;

/**
 * Represents the parameters have some pairs of a String key and a value.
 *
 * @author kawasima
 */
public class Parameters implements Map<String, Object>, Serializable {
    private static final long serialVersionUID = 1L;

    private final HashMap<String, Object> params = new HashMap<>();
    private boolean caseSensitive = true;

    protected Parameters() {

    }

    protected void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Fast ASCII-only lowercase for HTTP header names and parameter keys.
     * Avoids Locale-aware String.toLowerCase() overhead. Returns the same
     * String instance if it is already lowercase.
     */
    static String asciiLowerCase(String s) {
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                // Found uppercase — build lowered string from here
                char[] chars = s.toCharArray();
                chars[i] = (char) (c + 32);
                for (int j = i + 1; j < len; j++) {
                    char cj = chars[j];
                    if (cj >= 'A' && cj <= 'Z') {
                        chars[j] = (char) (cj + 32);
                    }
                }
                return new String(chars);
            }
        }
        return s; // already lowercase
    }
    /**
     * Creates an empty {@code Parameters} instance.
     *
     * @return a new empty parameters map
     */
    public static Parameters empty() {
        return new Parameters();
    }

    /**
     * Creates a {@code Parameters} instance containing one key-value pair.
     *
     * @param k1 the key
     * @param v1 the value
     * @return a new parameters map with the given entry
     */
    public static Parameters of(String k1, Object v1) {
        Parameters params = empty();
        params.put(k1, v1);
        return params;
    }

    /**
     * Creates a {@code Parameters} instance containing two key-value pairs.
     *
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @return a new parameters map with the given entries
     */
    public static Parameters of(String k1, Object v1, String k2, Object v2) {
        Parameters params = Parameters.of(k1, v1);
        params.put(k2, v2);
        return params;
    }

    /**
     * Creates a {@code Parameters} instance containing three key-value pairs.
     *
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @return a new parameters map with the given entries
     */
    public static Parameters of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Parameters params = Parameters.of(k1, v1, k2, v2);
        params.put(k3, v3);
        return params;
    }

    /**
     * Creates a {@code Parameters} instance containing four key-value pairs.
     *
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value
     * @return a new parameters map with the given entries
     */
    public static Parameters of(String k1, Object v1, String k2, Object v2, String k3, Object v3,  String k4, Object v4) {
        Parameters params = Parameters.of(k1, v1, k2, v2, k3, v3);
        params.put(k4, v4);
        return params;
    }

    /**
     * Creates a {@code Parameters} instance from a varargs array of alternating keys and values.
     *
     * @param init alternating key-value pairs; length must be even
     * @return a new parameters map with the given entries
     * @throws MisconfigurationException if the array length is odd
     */
    public static Parameters of(Object... init) {
        if (init.length % 2 != 0) {
            throw new MisconfigurationException("core.MISSING_KEY_VALUE_PAIR");
        }
        Parameters params = Parameters.empty();
        for(int i = 0; i < init.length; i += 2) {
            params.put(Objects.toString(init[i]), init[i + 1]);
        }
        return params;
    }

    /**
     *
     * @return {@inheritDoc}
     */
    @Override
    public int size() {
        return params.size();
    }

    /**
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return params.isEmpty();
    }

    /**
     *
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        if (!caseSensitive && key instanceof String s) {
            key = asciiLowerCase(s);
        }
        return params.containsKey(key);
    }

    /**
     *
     * @param value {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return params.containsValue(value);
    }

    /**
     *
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public String get(Object key) {
        if (!caseSensitive && key instanceof String s) {
            key = asciiLowerCase(s);
        }
        Object val = params.get(key);
        if (val == null) return null;
        return val.toString();
    }

    private Integer keyToInt(Object key) {
        int i;
        if (key instanceof Number n) {
            i = n.intValue();
        } else {
            try {
                i = Integer.parseInt((key.toString()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return i;
    }

    /**
     * Get a nested value.
     *
     * @param key first key
     * @param keys rest keys
     * @return found object
     */
    public Object getIn(Object key, Object... keys) {
        int idx = 0;
        Object current = getRawType(key);

        while (current != null && idx < keys.length) {
            if (keys[idx] == null) return null;

            if (current instanceof List<?> list) {
                Integer i = keyToInt(keys[idx]);
                if (i == null || i < 0 || i >= list.size()) return null;

                current = list.get(i);
            } else if (current instanceof Parameters map) {
                current = map.getRawType(keys[idx]);
            } else {
                return null;
            }
            idx++;
        }

        return current;
    }

    /**
     * Returns the raw (uncoerced) value associated with the given key.
     *
     * @param key the key to look up
     * @return the raw value, or {@code null} if not present
     */
    public Object getRawType(Object key) {
        if (!caseSensitive && key instanceof String s) {
            key = asciiLowerCase(s);
        }

        return params.get(key.toString());
    }

    /**
     * Returns the value at the nested key path as a {@link List}.
     * If the value is a single element, it is wrapped in a new list.
     * If the value is {@code null}, an empty list is returned.
     *
     * @param key  the first key
     * @param keys the remaining keys for nested lookup
     * @param <T>  the element type
     * @return a list of values, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(Object key, Object... keys) {
        T value = (T) getIn(key, keys);
        if (value == null) {
            return new ArrayList<>();
        } else if (value instanceof List<?> list) {
            return (List<T>) list;
        } else {
            List<T> values = new ArrayList<>();
            values.add(value);
            return values;
        }
    }

    /**
     * Returns the value at the nested key path parsed as a {@link Long}.
     *
     * @param key  the first key
     * @param keys the remaining keys for nested lookup
     * @return the parsed long value, or {@code null} if not present or not parseable
     */
    public Long getLong(Object key, Object... keys) {
        Object value = getIn(key, keys);
        if (value == null) {
            return null;
        } else {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object put(String key, Object value) {
        if (!caseSensitive) {
            key = asciiLowerCase(key);
        }
        Object v = params.get(key);
        if (v == null) {
            params.put(key, value);
        } else if (v instanceof List<?> list) {
            ((List<Object>)list).add(value);
        } else {
            List<Object> values = new ArrayList<>();
            values.add(v);
            values.add(value);
            params.put(key, values);
        }
        return v;
    }

    @Override
    public Object remove(Object key) {
        if (!caseSensitive) {
            key = asciiLowerCase(key.toString());
        }
        return params.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        m.forEach(this::put);
    }

    @Override
    public Object replace(String key, Object value) {
        if (!caseSensitive) {
            key = asciiLowerCase(key);
        }
        return params.replace(key, value);
    }

    @Override
    public void clear() {
        params.clear();
    }

    @Override
    public Set<String> keySet() {
        return params.keySet();
    }

    @Override
    public Collection<Object> values() {
        return params.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return params.entrySet();
    }

    @Override
    public String toString() {
        return params.toString();
    }

}
