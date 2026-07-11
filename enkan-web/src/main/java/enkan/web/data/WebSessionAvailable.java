package enkan.web.data;

import enkan.data.SessionAvailable;

import org.jspecify.annotations.Nullable;

/**
 * @author kawasima
 */
public interface WebSessionAvailable extends SessionAvailable {
    default @Nullable String getSessionKey() {
        Object key = getExtension("session/key");
        return key != null ? key.toString() : null;
    }

    default void setSessionKey(String key) {
        setExtension("session/key", key);
    }
}
