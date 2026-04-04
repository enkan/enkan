package enkan.web.data;

import enkan.data.SessionAvailable;

/**
 * @author kawasima
 */
public interface WebSessionAvailable extends SessionAvailable {
    default String getSessionKey() {
        Object key = getExtension("session/key");
        return key != null ? key.toString() : null;
    }

    default void setSessionKey(String key) {
        setExtension("session/key", key);
    }
}
