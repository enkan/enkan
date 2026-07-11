package enkan.data;

import org.jspecify.annotations.Nullable;

import java.security.Principal;

/**
 * @author kawasima
 */
public interface PrincipalAvailable extends Extendable {
    default @Nullable Principal getPrincipal() {
        return getExtension("principal");
    }

    default void setPrincipal(Principal principal) {
        setExtension("principal", principal);
    }
}
