package enkan.util;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.Set;

/**
 * Represents the utility class for threading functions.
 *
 * @author kawasima
 */
public class ThreadingUtils {
    private static final Set<Class<? extends Exception>> DEFAULT_ILLEGAL_ARGUMENT_EXCEPTIONS =
            Set.of(URISyntaxException.class,
                    MalformedURLException.class,
                    UnsupportedEncodingException.class,
                    UnsupportedCharsetException.class);

    @SuppressWarnings("unchecked")
    private static <X, Y> Optional<Y> doSome(X start, ThreadingFunction<?,?>... functions) {
        if (functions == null || start == null) {
            return Optional.ofNullable((Y) start);
        }

        Object v = start;
        SequencedCollection<ThreadingFunction<?,?>> funcQueue = new LinkedList<>(Arrays.asList(functions));
        while(!funcQueue.isEmpty()) {
            ThreadingFunction<Object, ?> typedFunction = (ThreadingFunction<Object, ?>) funcQueue.removeFirst();
            try {
                v = typedFunction.apply(v);
            } catch (Exception e) {
                if (DEFAULT_ILLEGAL_ARGUMENT_EXCEPTIONS.contains(e.getClass())) {
                    throw new IllegalArgumentException(e);
                } else if (e instanceof RuntimeException re) {
                    throw re;
                } else {
                    throw new RuntimeException(e);
                }
            }
            if (v == null) {
                return Optional.empty();
            }
        }
        return Optional.of((Y) v);
    }

    /**
     * Applies the given functions to the start value.
     *
     * @param start the start value
     * @param f1 the first function
     * @return the result of the last function
     * @param <X> the type of the start value
     * @param <Y> the type of the result value
     */
    public static <X, Y> Optional<Y> some(X start, ThreadingFunction<X, Y> f1) {
        return doSome(start, f1);
    }

    /**
     *  Applies the given two functions to the start value.
     *
     * @param start the start value
     * @param f1 the first function
     * @param f2 the second function
     * @return the result of the last function
     * @param <X0> the type of the start value
     * @param <X1> the type of the intermediate value
     * @param <Y> the type of the result value
     */
    public static <X0, X1, Y> Optional<Y> some(X0 start, ThreadingFunction<X0, X1> f1, ThreadingFunction<X1, Y> f2) {
        return doSome(start, f1, f2);
    }

    /**
     *  Applies the given three functions to the start value.
     * @param start the start value
     * @param f1 the first function
     * @param f2 the second function
     * @param f3 the third function
     * @return the result of the last function
     * @param <X0> the type of the start value
     * @param <X1> the type of the intermediate value
     * @param <X2> the type of the intermediate value
     * @param <Y> the type of the result value
     */
    public static <X0, X1, X2, Y> Optional<Y> some(X0 start,
                                         ThreadingFunction<X0, X1> f1,
                                         ThreadingFunction<X1, X2> f2,
                                         ThreadingFunction<X2, Y> f3) {
        return doSome(start, f1, f2, f3);
    }

    /**
     * Applies the given four functions to the start value.
     * @param start the start value
     * @param f1 the first function
     * @param f2 the second function
     * @param f3 the third function
     * @param f4 the fourth function
     * @return the result of the last function
     * @param <X0> the type of the start value
     * @param <X1> the type of the intermediate value
     * @param <X2> the type of the intermediate value
     * @param <X3> the type of the intermediate value
     * @param <Y> the type of the result value
     */
    public static <X0, X1, X2, X3, Y> Optional<Y> some(
            X0 start,
            ThreadingFunction<X0, X1> f1,
            ThreadingFunction<X1, X2> f2,
            ThreadingFunction<X2, X3> f3,
            ThreadingFunction<X3, Y> f4) {
        return doSome(start, f1, f2, f3, f4);
    }

    /**
     * Partially applies the given function with the given argument.
     *
     * @param f the function
     * @param arg the argument
     * @return the partially applied function
     * @param <X> the type of the first argument
     * @param <X1> the type of the second argument
     * @param <Y> the type of the result value
     */
    public static <X, X1, Y> ThreadingFunction<X, Y> partial(ThreadingBiFunction<X, X1, Y> f, X1 arg) {
        return x -> f.apply(x, arg);
    }
}
