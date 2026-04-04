package enkan.web.middleware.normalizer;

/**
 * @author kawasima
 */
public interface Normalizer<T> {
    default boolean canNormalize(Class<?> valueClass) {
        return true;
    }

    /**
     * Normalizes the given value.
     *
     * @param value the raw value
     * @return a normalized value
     */
    T normalize(T value);
}
