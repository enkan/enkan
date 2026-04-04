package enkan.web.middleware.normalizer;

/**
 * @author kawasima
 */
public class TrimNormalizer implements Normalizer<String> {
    @Override
    public boolean canNormalize(Class<?> valueClass) {
        return String.class.isAssignableFrom(valueClass);
    }

    @Override
    public String normalize(String value) {
        return value.trim();
    }
}
