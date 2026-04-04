package enkan.web.data;

import enkan.data.Extendable;

/**
 * Anti-forgery token support.
 *
 * @author kawasima
 */
public interface ForgeryDetectable extends Extendable {
    default String getAntiForgeryToken() {
        return getExtension("antiForgeryToken");
    }

    default void setAntiForgeryToken(String token) {
        setExtension("antiForgeryToken", token);
    }
}
