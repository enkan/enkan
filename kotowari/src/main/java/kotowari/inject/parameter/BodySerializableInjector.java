package kotowari.inject.parameter;

import enkan.web.data.HttpRequest;
import kotowari.data.BodyDeserializable;
import kotowari.inject.RuntimeParameterInjector;

/**
 * Injects the deserialized request body when it is type-compatible.
 *
 * <p>Implements {@link RuntimeParameterInjector} because applicability
 * depends on the actual deserialized body type, which is only available
 * at request time.
 *
 * @param <T> the type of the injected body object
 */
public class BodySerializableInjector<T> implements RuntimeParameterInjector<T> {
    @Override
    public String getName() {
        return "Serializable Object";
    }

    /**
     * {@inheritDoc}
     *
     * <p>Always returns {@code false} because body type compatibility
     * cannot be determined from the parameter type alone.
     */
    @Override
    public boolean isApplicable(Class<?> type) {
        return false;
    }

    @Override
    public boolean isApplicable(Class<?> type, HttpRequest request) {
        if (request instanceof BodyDeserializable bd) {
            Object bodyObj = bd.getDeserializedBody();
            return bodyObj != null && type.isAssignableFrom(bodyObj.getClass());
        }
        return false;
    }

    @Override
    public T getInjectObject(HttpRequest request) {
        return ((BodyDeserializable) request).getDeserializedBody();
    }
}
