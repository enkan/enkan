package enkan.util.sf;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered parameters attached to an Item or Inner List (RFC 8941 §3.1.2).
 *
 * @author kawasima
 */
public record SfParameters(LinkedHashMap<String, SfValue> map) {

    /** Empty parameters singleton — avoids allocation for parameter-less items. */
    public static final SfParameters EMPTY = new SfParameters(new LinkedHashMap<>(0));

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public SfValue get(String key) {
        return map.get(key);
    }

    public Map<String, SfValue> asMap() {
        return Collections.unmodifiableMap(map);
    }

    public int size() {
        return map.size();
    }
}
