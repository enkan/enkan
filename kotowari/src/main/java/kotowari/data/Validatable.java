package kotowari.data;

import enkan.collection.Multimap;
import enkan.data.Extendable;
import enkan.util.ThreadingUtils;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @author kawasima
 */
public interface Validatable extends Extendable {
    default boolean hasErrors() {
        Multimap<String, Object> errors = getExtension("errors");
        return errors != null && !errors.isEmpty();
    }

    default boolean hasErrors(String key) {
        Multimap<String, Object> errors =  getExtension("errors");
        return ThreadingUtils.some(errors,
                e -> e.getAll(key),
                e -> !e.isEmpty()).orElse(false);
    }

    default List<Object> getErrors(String key) {
        Multimap<String, Object> errors = getErrors();
        return errors == null ? List.of() : errors.getAll(key);
    }

    default @Nullable Multimap<String, Object> getErrors() {
        return getExtension("errors");
    }

    default void setErrors(Multimap<String, Object> errors) {
        setExtension("errors", errors);
    }
}
