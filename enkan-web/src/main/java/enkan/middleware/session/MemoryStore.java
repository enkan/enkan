package enkan.middleware.session;

import java.io.Closeable;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author kawasima
 */
public class MemoryStore implements KeyValueStore, Closeable {
    private static final long DEFAULT_TTL_SECONDS = 1800L;
    private static final long PURGE_INTERVAL_SECONDS = 60L;

    private record Entry(Serializable value, long expiresAt) {}

    private final ConcurrentHashMap<String, Entry> sessionMap = new ConcurrentHashMap<>();
    private long ttlSeconds = DEFAULT_TTL_SECONDS;
    private final ScheduledExecutorService purgeScheduler;

    public MemoryStore() {
        purgeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "enkan-memory-store-purge");
            t.setDaemon(true);
            return t;
        });
        purgeScheduler.scheduleAtFixedRate(this::purgeExpired,
                PURGE_INTERVAL_SECONDS, PURGE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        sessionMap.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }

    @Override
    public void close() {
        purgeScheduler.shutdown();
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Serializable read(String key) {
        Entry entry = sessionMap.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiresAt()) {
            sessionMap.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public String write(String key, Serializable value) {
        if (key == null) {
            key = UUID.randomUUID().toString();
        }
        long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        sessionMap.put(key, new Entry(value, expiresAt));
        return key;
    }

    @Override
    public String delete(String key) {
        sessionMap.remove(key);
        return null;
    }

    @Override
    public boolean putIfAbsent(String key, Serializable value) {
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlSeconds * 1000L;
        Entry newEntry = new Entry(value, expiresAt);
        Entry existing = sessionMap.putIfAbsent(key, newEntry);
        if (existing == null) {
            return true;
        }
        // If existing entry is expired, replace it
        if (now > existing.expiresAt()) {
            return sessionMap.replace(key, existing, newEntry);
        }
        return false;
    }
}
