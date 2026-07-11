package enkan.data;

import org.jspecify.annotations.Nullable;

/**
 * @author kawasima
 */
public interface SessionAvailable extends Extendable {
    @Nullable Session getSession();
    void setSession(@Nullable Session session);
}
