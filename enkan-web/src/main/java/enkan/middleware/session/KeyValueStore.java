package enkan.middleware.session;

import java.io.Serializable;

/**
 * A simple key-value storage abstraction used by {@link enkan.middleware.SessionMiddleware}
 * to persist session data.
 *
 * <p>Implementations must be thread-safe as session operations can occur
 * concurrently across multiple requests.  The built-in implementation is
 * {@link enkan.middleware.session.MemoryStore}; production deployments
 * typically replace it with a Redis or database-backed store.
 *
 * <p>Session keys are opaque strings (typically random UUIDs).  The value
 * written to the store is the serialized {@link enkan.data.Session} object.
 *
 * @author kawasima
 */
public interface KeyValueStore {
    /**
     * Read the value.
     *
     * @param key a String contains a store key
     * @return a stored object
     */
    Serializable read(String key);

    /**
     * Write the value with the given key.
     *
     * @param key a String contains a store key
     * @param value a stored object
     * @return new store key
     */
    String write(String key, Serializable value);

    /**
     * Delete the key and the value.
     *
     * @param key a String contains a store key
     * @return a String contains a store key
     */
    String delete(String key);

    /**
     * Attempts to write the value only if no entry exists for the given key.
     * Useful for implementing compare-and-set patterns where only the first
     * writer should succeed (e.g., idempotency key processing).
     *
     * <p>The default implementation is not atomic (read-then-write).
     * Implementations backed by {@code ConcurrentHashMap} or Redis should
     * override this with a truly atomic operation.
     *
     * @param key   the store key
     * @param value the value to write
     * @return {@code true} if the value was written (key was absent),
     *         {@code false} if the key already existed
     */
    default boolean putIfAbsent(String key, Serializable value) {
        if (read(key) != null) {
            return false;
        }
        write(key, value);
        return true;
    }
}
