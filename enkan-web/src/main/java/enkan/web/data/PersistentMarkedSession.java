package enkan.web.data;

import enkan.data.Session;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author kawasima
 */
public final class PersistentMarkedSession extends Session {
    public boolean isNew() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        throw new UnsupportedOperationException("size");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("isEmpty");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        throw new UnsupportedOperationException("containsKey");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException("containsValue");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable get(Object key) {
        throw new UnsupportedOperationException("get");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable put(String key, Serializable value) {
        throw new UnsupportedOperationException("put");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable remove(Object key) {
        throw new UnsupportedOperationException("remove");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends String, ? extends Serializable> m) {
        throw new UnsupportedOperationException("putAll");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> keySet() {
        throw new UnsupportedOperationException("keySet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Serializable> values() {
        throw new UnsupportedOperationException("values");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<String, Serializable>> entrySet() {
        throw new UnsupportedOperationException("entrySet");
    }
}
