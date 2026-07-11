package kotowari.data;

import enkan.data.Extendable;

import org.jspecify.annotations.Nullable;

/**
 * Binds the http parameters to a specified form object.
 *
 * @author kawasima
 */
public interface BodyDeserializable extends Extendable {
    default <T> @Nullable T getDeserializedBody() {
        return getExtension("deserializedBody");
    }

    default <T> void setDeserializedBody(@Nullable T obj) {
        setExtension("deserializedBody", obj);
    }
}
