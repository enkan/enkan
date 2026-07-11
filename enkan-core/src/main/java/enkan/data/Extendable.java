package enkan.data;

import org.jspecify.annotations.Nullable;

/**
 * Allows arbitrary named extensions to be attached to an object at runtime.
 *
 * <p>Middleware often needs to attach extra data to a request object (e.g.
 * a parsed session, a resolved principal, or a deserialized body) without
 * knowing the concrete class of that object at compile time.
 * {@code Extendable} provides a uniform, string-keyed property bag that
 * fills this role.
 *
 * <p>Higher-level interfaces such as {@link enkan.data.SessionAvailable} and
 * {@link enkan.data.PrincipalAvailable} are implemented as default-method
 * wrappers around {@code getExtension}/{@code setExtension}, so the mixin
 * proxy created by {@link enkan.util.MixinUtils#mixin} only needs a single
 * backing store.
 *
 * @author kawasima
 */
public interface Extendable {
    /**
     * Returns the extension value bound to {@code name}, or {@code null} if
     * no value has been set.
     *
     * @param <T>  the expected type of the extension value
     * @param name the extension key
     * @return the extension value, or {@code null}
     */
    <T> @Nullable T getExtension(String name);

    /**
     * Binds {@code extension} to the key {@code name}, replacing any
     * previously stored value.
     *
     * @param <T>       the type of the extension value
     * @param name      the extension key
     * @param extension the value to store
     */
    <T> void setExtension(String name, T extension);
}
