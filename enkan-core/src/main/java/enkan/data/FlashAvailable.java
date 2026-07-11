package enkan.data;

import org.jspecify.annotations.Nullable;

/**
 * @author kawasima
 */
public interface FlashAvailable extends Extendable {
    default @Nullable Flash<?> getFlash() {
        return getExtension("flash");
    }

    default void setFlash(Flash<?> flash) {
        setExtension("flash", flash);
    }
}
