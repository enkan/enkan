package enkan.web.middleware.normalizer;

import com.ibm.icu.text.Transliterator;
import enkan.exception.MisconfigurationException;

/**
 * Normalizes the half-width characters are contained in the given string to full-width chars
 *
 * @author kawasima
 */
public class IcuNormalizer implements Normalizer<String> {
    private final Transliterator transliterator;

    public IcuNormalizer(String translitId) {
        try {
            transliterator = Transliterator.getInstance(translitId);
        } catch (IllegalArgumentException ex) {
            throw new MisconfigurationException("ILLEGAL_TRANSILIT_ID", translitId, ex);
        }
    }

    @Override
    public String normalize(String value) {
        return transliterator.transform(value);
    }
}
