package enkan.data;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A serializable session object that holds attributes across multiple requests.
 *
 * <p>Unlike {@code javax.servlet.HttpSession}, this class has no dependency on
 * the Servlet API and can be used in any transport layer.
 * Attributes are stored in a plain {@link java.util.HashMap} and the entire
 * session is serialized to the session store by
 * {@link enkan.web.middleware.SessionMiddleware}.
 *
 * <p>A freshly created {@code Session} is marked as {@link #isNew() new}.
 * Once it has been persisted to the backing store, {@link #persist()} is called
 * to clear that flag.
 *
 * @author kawasima
 */
public class Session implements Map<String, Serializable>, Serializable {
    private static final long serialVersionUID = 1L;

    /** Holds the attributes of session **/
    private final Map<String, Serializable> attrs;
    private boolean isNew = true;

    public Session() {
        attrs = new HashMap<>();
    }

    /**
     * Marks this session as having been persisted to the backing store.
     *
     * <p>After this call, {@link #isNew()} returns {@code false}.
     */
    public void persist() {
        isNew = false;
    }

    /**
     * Returns {@code true} if this session has not yet been persisted to the
     * backing store since it was created.
     *
     * @return {@code true} for a newly created session; {@code false} after
     *         {@link #persist()} has been called
     */
    public boolean isNew() {
        return isNew;
    }

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
    public Serializable get(Object key) {
        return attrs.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable put(String key, Serializable value) {
        return attrs.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable remove(Object key) {
        return attrs.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends String, ? extends Serializable> m) {
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
    public Collection<Serializable> values() {
        return attrs.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<String, Serializable>> entrySet() {
        return attrs.entrySet();
    }
}
