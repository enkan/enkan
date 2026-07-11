package enkan.web.data;

import enkan.data.Extendable;

import org.jspecify.annotations.Nullable;

/**
 * Anti-forgery token support.
 *
 * @author kawasima
 */
public interface ForgeryDetectable extends Extendable {
    default @Nullable String getAntiForgeryToken() {
        return getExtension("antiForgeryToken");
    }

    default void setAntiForgeryToken(String token) {
        setExtension("antiForgeryToken", token);
    }
}
