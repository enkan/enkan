package enkan.web.http.fields.sf;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered parameters attached to an Item or Inner List (RFC 8941 §3.1.2).
 * The parameter map preserves insertion order and is unmodifiable.
 *
 * @author kawasima
 */
public record SfParameters(Map<String, SfValue> map) {

    public SfParameters(Map<String, SfValue> map) {
        this.map = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /** Empty parameters singleton — avoids allocation for parameter-less items. */
    public static final SfParameters EMPTY = new SfParameters(new LinkedHashMap<>(0));

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public SfValue get(String key) {
        return map.get(key);
    }

    public int size() {
        return map.size();
    }
}
