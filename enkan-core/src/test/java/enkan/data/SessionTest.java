package enkan.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @Test
    void newSessionIsNew() {
        Session session = new Session();
        assertThat(session.isNew()).isTrue();
    }

    @Test
    void putStoresAndRetrievesData() {
        Session session = new Session();
        session.put("user", "alice");
        assertThat(session.get("user")).isEqualTo("alice");
    }

    @Test
    void persistClearsNewFlag() {
        Session session = new Session();
        assertThat(session.isNew()).isTrue();
        session.persist();
        assertThat(session.isNew()).isFalse();
    }

    @Test
    void sizeReflectsNumberOfEntries() {
        Session session = new Session();
        assertThat(session.size()).isZero();
        session.put("a", "1");
        session.put("b", "2");
        assertThat(session.size()).isEqualTo(2);
    }

    @Test
    void containsKeyReturnsTrueForExistingKey() {
        Session session = new Session();
        session.put("key", "value");
        assertThat(session.containsKey("key")).isTrue();
        assertThat(session.containsKey("missing")).isFalse();
    }

    @Test
    void isEmptyReturnsTrueForNewSession() {
        Session session = new Session();
        assertThat(session.isEmpty()).isTrue();
        session.put("key", "value");
        assertThat(session.isEmpty()).isFalse();
    }

    @Test
    void removeDeletesEntry() {
        Session session = new Session();
        session.put("key", "value");
        assertThat(session.remove("key")).isEqualTo("value");
        assertThat(session.containsKey("key")).isFalse();
    }

    @Test
    void clearEmptiesSession() {
        Session session = new Session();
        session.put("a", "1");
        session.put("b", "2");
        session.clear();
        assertThat(session.isEmpty()).isTrue();
        assertThat(session.size()).isZero();
    }

    @Test
    void containsValueReturnsTrueForStoredValue() {
        Session session = new Session();
        session.put("key", "value");
        assertThat(session.containsValue("value")).isTrue();
        assertThat(session.containsValue("other")).isFalse();
    }

    @Test
    void keySetReturnsAllKeys() {
        Session session = new Session();
        session.put("a", "1");
        session.put("b", "2");
        assertThat(session.keySet()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void valuesReturnsAllValues() {
        Session session = new Session();
        session.put("a", "1");
        session.put("b", "2");
        assertThat(session.values()).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    void entrySetReturnsAllEntries() {
        Session session = new Session();
        session.put("a", "1");
        assertThat(session.entrySet()).hasSize(1);
    }

    @Test
    void putAllAddsMultipleEntries() {
        Session session = new Session();
        Session other = new Session();
        other.put("x", "10");
        other.put("y", "20");
        session.putAll(other);
        assertThat(session.get("x")).isEqualTo("10");
        assertThat(session.get("y")).isEqualTo("20");
    }
}
