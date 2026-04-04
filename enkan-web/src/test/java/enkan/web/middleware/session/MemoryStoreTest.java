package enkan.web.middleware.session;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStoreTest {

    @Test
    void writeAndRead() {
        MemoryStore store = new MemoryStore();
        String key = store.write(null, "hello");
        assertThat(store.read(key)).isEqualTo("hello");
        store.close();
    }

    @Test
    void deleteReturnsNull() {
        MemoryStore store = new MemoryStore();
        String key = store.write(null, "value");
        store.delete(key);
        assertThat(store.read(key)).isNull();
        store.close();
    }

    @Test
    void writeWithExplicitKey() {
        MemoryStore store = new MemoryStore();
        store.write("my-key", "data");
        assertThat(store.read("my-key")).isEqualTo("data");
        store.close();
    }

    @Test
    void readMissingKeyReturnsNull() {
        MemoryStore store = new MemoryStore();
        assertThat(store.read("no-such-key")).isNull();
        store.close();
    }

    /**
     * MemoryStore now stores the reference directly (no serialization copy).
     * Mutations to the stored object after write() are visible on read().
     * This documents the current (reference-sharing) semantics.
     */
    @Test
    void storedReferenceIsShared() {
        MemoryStore store = new MemoryStore();
        Map<String, String> session = new HashMap<>();
        session.put("user", "alice");

        String key = store.write(null, (Serializable) session);

        // Mutate the map after storing — change is visible through the store.
        session.put("user", "bob");

        @SuppressWarnings("unchecked")
        Map<String, String> retrieved = (Map<String, String>) store.read(key);
        assertThat(retrieved.get("user")).isEqualTo("bob");
        store.close();
    }

    @Test
    void expiredEntryReturnsNull() throws InterruptedException {
        MemoryStore store = new MemoryStore();
        store.setTtlSeconds(0);
        String key = store.write(null, "expiring");
        // TTL = 0 means expiresAt = now; sleep 1 ms to ensure the clock advances.
        Thread.sleep(1);
        assertThat(store.read(key)).isNull();
        store.close();
    }
}
