package enkan.graalvm;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry that maps component classes to their pre-generated {@link ComponentBinder}
 * instances.  {@link EnkanFeature} populates this registry at GraalVM native image build time.
 * {@link NativeComponentInjector} looks up binders here at runtime.
 *
 * <p>Application code can also call {@link #register} explicitly in a static initializer to
 * register hand-written binders for classes not covered by the Feature.
 */
public final class NativeComponentRegistry {
    @SuppressWarnings("rawtypes")
    private static final ConcurrentHashMap<Class<?>, ComponentBinder> BINDERS = new ConcurrentHashMap<>();

    private NativeComponentRegistry() {}

    public static <T> void register(Class<T> componentClass,
                                                               ComponentBinder<T> binder) {
        BINDERS.put(componentClass, binder);
    }

    @SuppressWarnings("unchecked")
    public static <T> ComponentBinder<T> get(Class<T> componentClass) {
        return (ComponentBinder<T>) BINDERS.get(componentClass);
    }

    public static void unregister(Class<?> componentClass) {
        BINDERS.remove(componentClass);
    }
}
