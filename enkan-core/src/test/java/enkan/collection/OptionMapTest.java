package enkan.collection;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptionMapTest {

    @Test
    void emptyReturnsEmptyMap() {
        OptionMap m = OptionMap.empty();
        assertThat(m).isEmpty();
    }

    @Test
    void ofKeyValuePairsCreatesMap() {
        OptionMap m = OptionMap.of("name", "enkan", "version", 1);
        assertThat(m.get("name")).isEqualTo("enkan");
        assertThat(m.get("version")).isEqualTo(1);
        assertThat(m).hasSize(2);
    }

    @Test
    void ofOptionMapCopiesExisting() {
        OptionMap original = OptionMap.of("key", "value");
        OptionMap copy = OptionMap.of(original);
        assertThat(copy.get("key")).isEqualTo("value");

        // Modifying copy does not affect original
        copy.put("key", "changed");
        assertThat(original.get("key")).isEqualTo("value");
    }

    @Test
    void getStringReturnsValueAsString() {
        OptionMap m = OptionMap.of("name", "enkan", "count", 42);
        assertThat(m.getString("name")).isEqualTo("enkan");
        assertThat(m.getString("count")).isEqualTo("42");
    }

    @Test
    void getStringReturnsNullForMissingKey() {
        OptionMap m = OptionMap.empty();
        assertThat(m.getString("missing")).isNull();
    }

    @Test
    void getStringReturnsDefaultForMissingKey() {
        OptionMap m = OptionMap.empty();
        assertThat(m.getString("missing", "fallback")).isEqualTo("fallback");
    }

    @Test
    void getStringReturnsValueOverDefault() {
        OptionMap m = OptionMap.of("key", "actual");
        assertThat(m.getString("key", "fallback")).isEqualTo("actual");
    }

    @Test
    void getIntFromNumber() {
        OptionMap m = OptionMap.of("port", 8080);
        assertThat(m.getInt("port")).isEqualTo(8080);
    }

    @Test
    void getIntFromString() {
        OptionMap m = OptionMap.of("port", "9090");
        assertThat(m.getInt("port")).isEqualTo(9090);
    }

    @Test
    void getIntReturnsDefaultForMissingKey() {
        OptionMap m = OptionMap.empty();
        assertThat(m.getInt("missing")).isEqualTo(0);
        assertThat(m.getInt("missing", 42)).isEqualTo(42);
    }

    @Test
    void getLongFromNumber() {
        OptionMap m = OptionMap.of("size", 123456789L);
        assertThat(m.getLong("size")).isEqualTo(123456789L);
    }

    @Test
    void getLongFromString() {
        OptionMap m = OptionMap.of("size", "999999999999");
        assertThat(m.getLong("size")).isEqualTo(999999999999L);
    }

    @Test
    void getLongReturnsDefaultForMissingKey() {
        OptionMap m = OptionMap.empty();
        assertThat(m.getLong("missing")).isEqualTo(0L);
        assertThat(m.getLong("missing", 100L)).isEqualTo(100L);
    }

    @Test
    void getBooleanReturnsBoolean() {
        OptionMap m = OptionMap.of("enabled", true, "disabled", false);
        assertThat(m.getBoolean("enabled")).isTrue();
        assertThat(m.getBoolean("disabled")).isFalse();
    }

    @Test
    void getBooleanCoercesFromInt() {
        OptionMap m = OptionMap.of("zero", 0, "one", 1);
        assertThat(m.getBoolean("zero")).isFalse();
        assertThat(m.getBoolean("one")).isTrue();
    }

    @Test
    void getBooleanReturnsDefaultForMissingKey() {
        OptionMap m = OptionMap.empty();
        assertThat(m.getBoolean("missing")).isTrue();  // default is true
        assertThat(m.getBoolean("missing", false)).isFalse();
    }

    @Test
    void getListFromList() {
        List<Object> items = Arrays.asList("a", "b", "c");
        OptionMap m = OptionMap.of("items", items);
        assertThat(m.getList("items")).containsExactly("a", "b", "c");
    }

    @Test
    void getListFromArray() {
        Object[] arr = {"x", "y"};
        OptionMap m = OptionMap.of("items", arr);
        assertThat(m.getList("items")).containsExactly("x", "y");
    }

    @Test
    void getListFromScalar() {
        OptionMap m = OptionMap.of("single", "value");
        List<Object> result = m.getList("single");
        assertThat(result).containsExactly("value");
    }

    @Test
    void getListReturnsEmptyForNull() {
        OptionMap m = OptionMap.empty();
        assertThat(m.getList("missing")).isEmpty();
    }
}
