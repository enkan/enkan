package enkan.data;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A map-backed state container scoped to a long-running conversation.
 *
 * <p>While a {@link enkan.data.Session} spans the entire browser session,
 * a {@code ConversationState} is tied to a single multi-step workflow
 * (e.g. a wizard).  Multiple conversation states may coexist within one
 * session, each identified by the ID of its owning
 * {@link jakarta.enterprise.context.Conversation}.
 *
 * <p>Entries survive across requests that belong to the same conversation
 * and are discarded when the conversation ends.
 *
 * @author kawasima
 */
public class ConversationState implements Map<String, Object>, Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> attrs = new HashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return attrs.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return attrs.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return attrs.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return attrs.containsValue(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Object get(Object key) {
        return attrs.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Object put(String key, Object value) {
        return attrs.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Object remove(Object key) {
        return attrs.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends String, ?> m) {
        attrs.putAll(m);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        attrs.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> keySet() {
        return attrs.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Object> values() {
        return attrs.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<String, Object>> entrySet() {
        return attrs.entrySet();
    }
}

