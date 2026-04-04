package enkan.util;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static enkan.util.ReflectionUtils.tryReflection;

/**
 * Utility class for dynamically augmenting objects with additional interfaces
 * at runtime using JDK dynamic proxies.
 *
 * <p>Enkan middleware frequently needs to attach capabilities to a request
 * object — for example, marking it as {@code EntityManageable} or
 * {@code BodyDeserializable} — without knowing its concrete type at compile
 * time.  {@code MixinUtils.mixin(target, Interface.class)} wraps {@code target}
 * in a proxy that:
 * <ol>
 *   <li>Forwards calls declared by the original class to the original object.</li>
 *   <li>Delegates calls declared by the newly added interface(s) to their
 *       {@code default} implementations, which typically store/retrieve data
 *       via the {@link enkan.data.Extendable} property bag.</li>
 * </ol>
 *
 * <p>Subsequent calls to {@code mixin} on an already-proxied object reuse the
 * same underlying instance and simply extend the proxy's interface set, so the
 * cost of layering multiple interfaces is minimal.
 *
 * @author kawasima
 */
public class MixinUtils {
    /** Cache for default-method handles (unreflectSpecial). */
    private static final ConcurrentHashMap<Method, MethodHandle> methodHandleCache = new ConcurrentHashMap<>();
    /** Cache for original-object delegation handles (unreflect). */
    private static final ConcurrentHashMap<Method, MethodHandle> delegateHandleCache = new ConcurrentHashMap<>();
    /** Cache: (originalClass, mixinInterfaces) → merged interface array for Proxy. */
    private static final ConcurrentHashMap<List<Class<?>>, Class<?>[]> interfaceArrayCache = new ConcurrentHashMap<>();
    /** Cache: class → all implemented interfaces (avoids repeated class hierarchy traversal). */
    private static final ConcurrentHashMap<Class<?>, Class<?>[]> allInterfacesCache = new ConcurrentHashMap<>();
    /** Cache: interface array cache key → Proxy constructor MethodHandle (avoids Proxy.newProxyInstance + getConstructor per call). */
    private static final ConcurrentHashMap<List<Class<?>>, MethodHandle> proxyCtorCache = new ConcurrentHashMap<>();

    /**
     * Build a MethodHandle for a default method, normalized to
     * {@code (Object, Object[]) -> Object} via asSpreader.
     */
    private static MethodHandle lookupSpecial(Method m) {
        Class<?> declaringClass = m.getDeclaringClass();
        MethodHandle mh = tryReflection(() -> {
            // privateLookupIn requires the caller module to read the target module.
            // Establish the read edge dynamically so this works when declaringClass
            // is in a different named module (e.g. enkan.web calling from enkan.core).
            MixinUtils.class.getModule().addReads(declaringClass.getModule());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
            return lookup.unreflectSpecial(m, declaringClass);
        });
        int paramCount = m.getParameterCount();
        return mh.asType(MethodType.genericMethodType(1 + paramCount))
                  .asSpreader(Object[].class, paramCount);
    }

    /**
     * Build a MethodHandle for delegating to the original object, normalized
     * to the shape {@code (Object, Object[]) -> Object} via asSpreader so
     * the hot path avoids per-call array allocation.
     */
    private static MethodHandle lookupDelegate(Method m) {
        MethodHandle mh;
        try {
            mh = MethodHandles.publicLookup().unreflect(m);
        } catch (IllegalAccessException e) {
            mh = tryReflection(() -> {
                // privateLookupIn requires the caller module to read the target module.
                MixinUtils.class.getModule().addReads(m.getDeclaringClass().getModule());
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        m.getDeclaringClass(), MethodHandles.lookup());
                return lookup.unreflect(m);
            });
        }
        // Normalize to (Object, Object[]) -> Object so invoke() at call
        // site needs no array copy.
        int paramCount = m.getParameterCount();
        return mh.asType(MethodType.genericMethodType(1 + paramCount))
                  .asSpreader(Object[].class, paramCount);
    }

    static MethodHandle getMethodHandle(Method method) {
        return  methodHandleCache.computeIfAbsent(method, MixinUtils::lookupSpecial);
    }

    static MethodHandle getDelegateHandle(Method method) {
        return delegateHandleCache.computeIfAbsent(method, MixinUtils::lookupDelegate);
    }

    record MixinProxyHandler<T>(T original, Class<?>[] proxyInterfaces) implements InvocationHandler {


        @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getDeclaringClass().isAssignableFrom(original.getClass())) {
                    if (method.getName().equals("equals") && args.length == 1) {
                        return args[0] == proxy;
                    } else {
                        // Handle is pre-shaped to (Object, Object[]) via asSpreader
                        return getDelegateHandle(method)
                                .invoke(original, args != null ? args : new Object[0]);
                    }
                } else {
                    // Handle is pre-shaped to (Object, Object[]) via asSpreader
                    return getMethodHandle(method)
                            .invoke(proxy, args != null ? args : new Object[0]);
                }
            }
        }

    private static void getAllInterfaces(Class<?> clazz, final LinkedHashSet<Class<?>> interfacesFound) {
        while (clazz != null) {
            final Class<?>[] interfaces = clazz.getInterfaces();
            for (Class<?> i : interfaces) {
                if (interfacesFound.add(i)) {
                    getAllInterfaces(i, interfacesFound);
                }
            }

            clazz = clazz.getSuperclass();
        }

    }

    static Class<?>[] getAllInterfaces(final Class<?> clazz) {
        if (clazz == null) return null;
        return allInterfacesCache.computeIfAbsent(clazz, c -> {
            final LinkedHashSet<Class<?>> interfacesFound = new LinkedHashSet<>();
            getAllInterfaces(c, interfacesFound);
            return interfacesFound.toArray(new Class<?>[0]);
        });
    }

    /**
     * Returns a proxy that implements all interfaces currently implemented by
     * {@code target} <em>plus</em> the supplied {@code interfaces}.
     *
     * <p>If {@code target} is already a mixin proxy the new interfaces are
     * merged into the existing proxy rather than creating a nested proxy.
     * If {@code target} already implements every requested interface, it is
     * returned unchanged without creating a proxy.
     *
     * @param <T>        the type of the target object
     * @param target     the object to augment; {@code null} is returned as-is
     * @param interfaces one or more interfaces to add to the proxy
     * @return a proxy implementing all original interfaces plus the requested
     *         ones, or {@code target} itself if no new interfaces are needed
     */
    @SuppressWarnings("unchecked")
    public static <T> T mixin(T target, Class<?>... interfaces) {
        if (target == null) return null;

        final Class<?> targetClass = target.getClass();
        boolean allPresent = true;
        for (Class<?> iface : interfaces) {
            if (!iface.isAssignableFrom(targetClass)) {
                allPresent = false;
                break;
            }
        }
        if (allPresent) {
            return target;
        }

        // Extract the original object and its current proxy interfaces
        T original;
        Class<?>[] currentInterfaces;
        if (Proxy.isProxyClass(targetClass)) {
            MixinProxyHandler<T> handler = ((MixinProxyHandler<T>) Proxy.getInvocationHandler(target));
            original = handler.original();
            currentInterfaces = handler.proxyInterfaces();
        } else {
            original = target;
            currentInterfaces = getAllInterfaces(targetClass);
        }

        // Build cache key: [originalClass, currentInterfaces..., newInterfaces...]
        // Use List for proper equals/hashCode
        List<Class<?>> cacheKey = buildCacheKey(original.getClass(), currentInterfaces, interfaces);
        Class<?>[] classes = interfaceArrayCache.computeIfAbsent(cacheKey, k -> {
            Class<?>[] merged = new Class[currentInterfaces.length + interfaces.length];
            System.arraycopy(currentInterfaces, 0, merged, 0, currentInterfaces.length);
            System.arraycopy(interfaces, 0, merged, currentInterfaces.length, interfaces.length);
            // Pre-warm MethodHandle cache for new default methods
            for (Class<?> iface : interfaces) {
                for (Method m : iface.getMethods()) {
                    if (m.isDefault()) {
                        getMethodHandle(m);
                    }
                }
            }
            return merged;
        });

        MethodHandle ctor = proxyCtorCache.computeIfAbsent(cacheKey, k -> {
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                // Create a temporary proxy instance only to capture the generated proxy class;
                // the instance itself is discarded immediately.
                Class<?> proxyClass = Proxy.newProxyInstance(cl, classes,
                        (p, m, a) -> null).getClass();
                Constructor<?> cons = proxyClass.getConstructor(InvocationHandler.class);
                // Use publicLookup() so that enkan.core can unreflect the constructor of
                // jdk.proxy2.$ProxyN without needing the caller module to read jdk.proxy2.
                // (privateLookupIn would require a read edge that cannot be established here.)
                return MethodHandles.publicLookup()
                        .unreflectConstructor(cons)
                        .asType(MethodType.methodType(Object.class, InvocationHandler.class));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to create proxy constructor handle", e);
            }
        });
        try {
            return (T) ctor.invoke(new MixinProxyHandler<>(original, classes));
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create mixin proxy", e);
        }
    }

    private static List<Class<?>> buildCacheKey(Class<?> originalClass,
                                                 Class<?>[] currentInterfaces,
                                                 Class<?>[] newInterfaces) {
        Class<?>[] key = new Class<?>[1 + currentInterfaces.length + newInterfaces.length];
        key[0] = originalClass;
        System.arraycopy(currentInterfaces, 0, key, 1, currentInterfaces.length);
        System.arraycopy(newInterfaces, 0, key, 1 + currentInterfaces.length, newInterfaces.length);
        return Arrays.asList(key);
    }

    /** Counter for generating unique class names. */
    private static final AtomicLong classCounter = new AtomicLong();
    /** Cache: (superClass, interfaces...) → pre-built Supplier to avoid re-generating at runtime. */
    private static final ConcurrentHashMap<List<Class<?>>, Supplier<?>> factoryCache = new ConcurrentHashMap<>();

    /**
     * Map from generated class name (binary name, e.g. {@code enkan.data.DefaultHttpRequest$Mixin1})
     * to the raw class bytes produced by the Class-File API.
     *
     * <p>This is populated by {@link #createFactory} and is read by
     * {@code GenerateMixinConfig} at native-image build time (in the Maven
     * {@code prepare-package} phase) to write each {@code $Mixin} class as an
     * ordinary {@code .class} file into {@code target/classes/}.  The shade plugin
     * then includes those files in the fat JAR so {@code native-image} sees them
     * as normal compiled classes — no predefined-classes mechanism is required.
     * The map is never cleared so that the build-time step can access all generated
     * classes after calling {@code createFactory}.
     */
    public static final ConcurrentHashMap<String, byte[]> generatedClassBytes = new ConcurrentHashMap<>();

    /**
     * Creates a factory that produces instances of a runtime-generated subclass
     * that extends the concrete class of {@code template} and implements all
     * the given mixin interfaces.
     *
     * <p>The subclass is generated once using the Class-File API (JEP 484).
     * Since all mixin interfaces use {@code default} methods delegating to
     * {@link enkan.data.Extendable#getExtension}/{@link enkan.data.Extendable#setExtension},
     * no method body generation is needed — normal Java inheritance resolves
     * everything. Each call to {@code Supplier.get()} allocates only the
     * subclass instance itself (single {@code new} — no Proxy, no
     * InvocationHandler).
     *
     * @param template   a sample object used to resolve the concrete class;
     *                   not retained after this call
     * @param interfaces the mixin interfaces to add
     * @param <T>        the type of the target object
     * @return a supplier that produces pre-mixed instances
     */
    @SuppressWarnings("unchecked")
    public static <T> Supplier<T> createFactory(T template, Class<?>... interfaces) {
        Class<?> superClass = template.getClass();

        // Build cache key: [superClass, interfaces...]
        List<Class<?>> cacheKey = new ArrayList<>(interfaces.length + 1);
        cacheKey.add(superClass);
        for (Class<?> iface : interfaces) {
            cacheKey.add(iface);
        }
        Supplier<?> cached = factoryCache.get(cacheKey);
        if (cached != null) {
            return (Supplier<T>) cached;
        }

        Supplier<T> factory = buildFactory(superClass, interfaces);
        Supplier<?> existing = factoryCache.putIfAbsent(cacheKey, factory);
        return existing != null ? (Supplier<T>) existing : factory;
    }

    @SuppressWarnings("unchecked")
    private static <T> Supplier<T> buildFactory(Class<?> superClass, Class<?>[] interfaces) {
        // Collect only interfaces not already implemented by superClass
        List<ClassDesc> newIfaceDescs = new ArrayList<>();
        for (Class<?> iface : interfaces) {
            if (!iface.isAssignableFrom(superClass)) {
                newIfaceDescs.add(ClassDesc.ofDescriptor(iface.descriptorString()));
            }
        }

        if (newIfaceDescs.isEmpty()) {
            // All interfaces already present — just return the no-arg constructor
            try {
                Constructor<T> ctor = (Constructor<T>) superClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                return () -> {
                    try { return ctor.newInstance(); }
                    catch (ReflectiveOperationException e) {
                        throw new RuntimeException("Failed to create instance", e);
                    }
                };
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No no-arg constructor on " + superClass.getName(), e);
            }
        }

        ClassDesc superDesc = ClassDesc.ofDescriptor(superClass.descriptorString());
        long id = classCounter.incrementAndGet();
        ClassDesc genDesc = ClassDesc.of(superClass.getName() + "$Mixin" + id);

        byte[] bytes = ClassFile.of().build(genDesc, cb -> {
            // Target Java 17 class-file format (major version 61) so that the
            // GraalVM native-image predefined-classes mechanism (which uses an
            // older bundled ASM) can parse the bytes.  The generated class only
            // uses Java 17-compatible bytecode features.
            cb.withVersion(ClassFile.JAVA_17_VERSION, 0);
            cb.withSuperclass(superDesc);
            cb.withInterfaceSymbols(newIfaceDescs);
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            // no-arg constructor calling super()
            cb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void),
                    ClassFile.ACC_PUBLIC, code -> {
                        code.aload(0);
                        code.invokespecial(superDesc, "<init>",
                                MethodTypeDesc.of(ConstantDescs.CD_void));
                        code.return_();
                    });
        });

        String generatedName = superClass.getName() + "$Mixin" + id;
        try {
            // privateLookupIn requires the caller module to read the target module.
            MixinUtils.class.getModule().addReads(superClass.getModule());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    superClass, MethodHandles.lookup());
            // Store bytes so KotowariFeature (or GenerateMixinConfig) can retrieve them
            // at native-image build time and write the class file to target/classes/.
            generatedClassBytes.put(generatedName, bytes);
            Class<?> generated;
            try {
                generated = lookup.defineClass(bytes);
            } catch (LinkageError le) {
                // Class already loaded (e.g. written to target/classes/ and compiled into
                // the native image). Find it by name in the current class loader.
                try {
                    generated = Class.forName(generatedName, true,
                            superClass.getClassLoader());
                } catch (ClassNotFoundException cnfe) {
                    throw new RuntimeException("Class " + generatedName
                            + " already loaded but not findable", cnfe);
                }
            }
            MethodHandle ctorHandle = lookup.findConstructor(
                    generated, MethodType.methodType(void.class));
            return () -> {
                try { return (T) ctorHandle.invoke(); }
                catch (Throwable e) {
                    throw new RuntimeException("Failed to create pre-mixed instance", e);
                }
            };
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access " + superClass.getName() + " for class generation", e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Generated class missing no-arg constructor", e);
        }
    }
}
