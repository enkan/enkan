package enkan.util;

import org.jspecify.annotations.Nullable;

/**
 * ThreadingFunction is a functional interface that can throw an exception.
 *
 * @author kawasima
 */
@FunctionalInterface
public interface ThreadingFunction<T, R> {
    @Nullable R apply(T t) throws Exception;
}
