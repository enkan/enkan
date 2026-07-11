package kotowari.graalvm;

/**
 * Holds the singleton {@link KotowariDispatcherInvoker} instance generated at build time.
 * {@link KotowariFeature} populates this at native image build time;
 * {@link NativeControllerInvokerMiddleware} reads it at runtime.
 *
 * <p>This class is initialized at build time via
 * {@code --initialize-at-build-time=kotowari.graalvm.NativeDispatcherRegistry}.
 * The {@code invoker} field is therefore frozen in the native image heap and is
 * effectively read-only at runtime — {@link #register} is never called after
 * image startup.  The field is plain {@code static} (not {@code volatile}) because
 * no memory-visibility guarantee is needed once the value is baked into the heap.
 */
public final class NativeDispatcherRegistry {
    private static @org.jspecify.annotations.Nullable KotowariDispatcherInvoker invoker;

    private NativeDispatcherRegistry() {}

    public static void register(KotowariDispatcherInvoker inv) {
        invoker = inv;
    }

    public static @org.jspecify.annotations.Nullable KotowariDispatcherInvoker get() {
        return invoker;
    }
}
