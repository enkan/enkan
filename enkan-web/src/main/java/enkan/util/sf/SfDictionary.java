package enkan.util.sf;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Dictionary in an RFC 8941 Structured Field (§3.2).
 * Members are keyed by string and are either {@link SfItem} or {@link SfInnerList}.
 *
 * @author kawasima
 */
public record SfDictionary(LinkedHashMap<String, SfMember> members) {

    public SfDictionary {
        members = new LinkedHashMap<>(members);
    }

    /**
     * Returns the member for the given key, cast to the expected type.
     *
     * @param key  the dictionary key
     * @param type the expected type ({@link SfItem} or {@link SfInnerList})
     * @param <T>  the expected type
     * @return the member, or {@code null} if absent
     */
    public <T extends SfMember> T get(String key, Class<T> type) {
        return type.cast(members.get(key));
    }

    public boolean containsKey(String key) {
        return members.containsKey(key);
    }

    public Map<String, SfMember> asMap() {
        return Collections.unmodifiableMap(members);
    }

    public int size() {
        return members.size();
    }
}
