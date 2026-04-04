package kotowari.graalvm;

import enkan.util.MixinUtils;
import kotowari.routing.Route;
import kotowari.routing.Routes;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.lang.constant.ConstantDescs.*;

/**
 * GraalVM {@link Feature} that generates a reflection-free {@code KotowariDispatcher} class
 * at native image build time.
 *
 * <p>At build time this Feature:
 * <ol>
 *   <li>Resolves all registered {@link Routes} via {@link RouteRegistry} (or the
 *       {@code kotowari.routes.factory} system property).</li>
 *   <li>Extracts controller class names and action names from the route list.</li>
 *   <li>Registers all controller classes and their action methods for reflection
 *       (needed to instantiate controllers and resolve {@link kotowari.inject.ParameterInjector}s).</li>
 *   <li>Generates a {@code KotowariDispatcher} class (using the Class File API) with a
 *       {@code dispatch(String, Object, Object[])} method that dispatches via a series of
 *       string equality checks and direct {@code invokevirtual} call sites.</li>
 * </ol>
 *
 * <p>Usage — in your application:
 * <pre>{@code
 * // Option A: static registry
 * RouteRegistry.register(Routes.define(r -> { ... }).compile());
 *
 * // Option B: system property at native-image build time
 * // -Dkotowari.routes.factory=com.example.AppRoutes
 * // where AppRoutes has: public static Routes routes() { ... }
 * }</pre>
 */
public class KotowariFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Routes routes = resolveRoutes(access);
        if (routes == null) {
            return;
        }

        List<RouteEntry> entries = extractEntries(routes);
        if (entries.isEmpty()) {
            return;
        }

        registerControllerReflection(access, entries);
        pregenerateMixinClasses(access);

        byte[] dispatcherBytes = generateDispatcher(entries);
        Class<?> dispatcherClass = defineDispatcherClass(dispatcherBytes);
        access.registerAsUsed(dispatcherClass);
        try {
            KotowariDispatcherInvoker inv =
                    (KotowariDispatcherInvoker) dispatcherClass.getConstructor().newInstance();
            NativeDispatcherRegistry.register(inv);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate KotowariDispatcher", e);
        }
    }

    /**
     * Invokes the factory's {@code buildApp()} method (if available) to trigger
     * {@link enkan.util.MixinUtils#createFactory} in the build JVM, populating
     * {@link MixinUtils#factoryCache}.  Because {@code MixinUtils} is initialized
     * at build time (via {@code --initialize-at-build-time=enkan.util.MixinUtils}),
     * the cache — including the {@code Supplier} holding the pre-built
     * {@code MethodHandle} — is serialized into the native image heap and reused
     * at runtime without re-generating any class.
     *
     * <p>The mixin {@code .class} files are written to {@code target/classes/} by the
     * {@code GenerateMixinConfig} Maven exec step (in {@code prepare-package}) so
     * that the shade JAR contains them as ordinary compiled classes before
     * native-image starts.
     */
    private void pregenerateMixinClasses(BeforeAnalysisAccess access) {
        String factoryClassName = System.getProperty("kotowari.routes.factory");
        if (factoryClassName == null) {
            return;
        }
        try {
            Class<?> factoryClass = access.findClassByName(factoryClassName);
            if (factoryClass == null) {
                return;
            }
            Method buildAppMethod;
            try {
                buildAppMethod = factoryClass.getMethod("buildApp");
            } catch (NoSuchMethodException e) {
                // Factory does not expose buildApp(); mixin pre-generation skipped.
                return;
            }
            Object app = buildAppMethod.invoke(null);
            // Trigger buildRequestFactory() -> MixinUtils.createFactory().
            // The resulting Supplier is cached in MixinUtils.factoryCache and will be
            // reused at runtime (no duplicate defineClass call needed).
            Method createRequest = app.getClass().getMethod("createRequest");
            createRequest.invoke(app);

            // Register every $Mixin class for reflection so that any reflective
            // access patterns in the framework still work.
            for (String mixinName : MixinUtils.generatedClassBytes.keySet()) {
                try {
                    Class<?> mixinClass = Class.forName(mixinName);
                    RuntimeReflection.register(mixinClass);
                    RuntimeReflection.registerAllConstructors(mixinClass);
                    access.registerAsUsed(mixinClass);
                    System.out.println("[KotowariFeature] Registered mixin class: " + mixinName);
                } catch (ClassNotFoundException e) {
                    System.err.println("[KotowariFeature] Mixin class not loadable: " + mixinName);
                }
            }
        } catch (Exception e) {
            System.err.println("[KotowariFeature] Could not pre-generate mixin classes: "
                    + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private Routes resolveRoutes(BeforeAnalysisAccess access) {
        Routes routes = RouteRegistry.get();
        if (routes != null) {
            return routes;
        }

        String factoryClassName = System.getProperty("kotowari.routes.factory");
        if (factoryClassName == null) {
            return null;
        }

        try {
            Class<?> factoryClass = access.findClassByName(factoryClassName);
            if (factoryClass == null) {
                return null;
            }
            Method routesMethod = factoryClass.getMethod("routes");
            return (Routes) routesMethod.invoke(null);
        } catch (Exception e) {
            System.err.println("[KotowariFeature] Could not invoke routes factory: " + e.getMessage());
            return null;
        }
    }

    List<RouteEntry> extractEntries(Routes routes) {
        List<RouteEntry> entries = new ArrayList<>();
        for (Route route : routes.getRouteList()) {
            enkan.collection.OptionMap constraints = route.constraints();
            String controllerName = constraints.getString("controller");
            String action = constraints.getString("action");
            if (controllerName != null && action != null) {
                entries.add(new RouteEntry(controllerName, action));
            }
        }
        return entries;
    }

    private void registerControllerReflection(BeforeAnalysisAccess access, List<RouteEntry> entries) {
        for (RouteEntry entry : entries) {
            Class<?> ctrl = access.findClassByName(entry.controllerClassName());
            if (ctrl == null) {
                continue;
            }
            RuntimeReflection.register(ctrl);
            RuntimeReflection.registerAllConstructors(ctrl);
            // Register @Inject fields and @PostConstruct methods so ComponentInjector
            // can wire the controller instance in native mode.
            RuntimeReflection.registerAllDeclaredFields(ctrl);
            RuntimeReflection.registerAllDeclaredMethods(ctrl);

            // Auto-register parameter types and return types of all public methods
            // so Jackson can deserialize request bodies and serialize responses without
            // manual reflect-config entries. Generic type arguments (e.g. List<Customer>)
            // are also traversed to catch container return types.
            // getMethods() includes inherited public methods; getDeclaredMethods() would miss
            // action methods defined in a controller superclass.
            Set<Class<?>> appTypes = new LinkedHashSet<>();
            for (Method m : ctrl.getMethods()) {
                if (m.getDeclaringClass() == Object.class) continue;
                for (Type t : m.getGenericParameterTypes()) {
                    collectReachableTypes(t, appTypes);
                }
                collectReachableTypes(m.getGenericReturnType(), appTypes);
            }
            for (Class<?> t : appTypes) {
                RuntimeReflection.register(t);
                RuntimeReflection.registerAllConstructors(t);
                RuntimeReflection.registerAllDeclaredFields(t);
                RuntimeReflection.registerAllDeclaredMethods(t);
            }
        }
    }

    /**
     * Collects types reachable from {@code type} that need manual reflection registration
     * (i.e. application classes not covered by GraalVM's built-in analysis).
     *
     * <p>Handles raw classes, arrays, parameterized types (e.g. {@code List<Customer>}),
     * and wildcard bounds. Recursively traverses declared field types to handle nested
     * objects (e.g. embedded DTOs). Cycles are prevented by the {@code result} set: once
     * a type is added it is not visited again, so traversal terminates even for
     * mutually-referential types.
     */
    void collectReachableTypes(Type type, Set<Class<?>> result) {
        if (type instanceof Class<?> cls) {
            if (cls.isPrimitive() || cls == void.class) return;
            if (cls.isArray()) {
                collectReachableTypes(cls.getComponentType(), result);
                return;
            }
            if (shouldSkipType(cls)) return;
            if (!result.add(cls)) return; // already seen, avoid cycles
            try {
                for (Field f : cls.getDeclaredFields()) {
                    collectReachableTypes(f.getGenericType(), result);
                }
            } catch (SecurityException e) {
                // Module system may deny field access for some types; skip field traversal
            }
        } else if (type instanceof ParameterizedType pt) {
            collectReachableTypes(pt.getRawType(), result);
            for (Type arg : pt.getActualTypeArguments()) {
                collectReachableTypes(arg, result);
            }
        } else if (type instanceof WildcardType wt) {
            for (Type b : wt.getUpperBounds()) collectReachableTypes(b, result);
            for (Type b : wt.getLowerBounds()) collectReachableTypes(b, result);
        }
        // TypeVariable: skip — resolved only at runtime
    }

    /**
     * Returns {@code true} for types that do not need manual reflection registration:
     * JDK types, framework types (enkan.* and known kotowari framework sub-packages).
     *
     * <p>Note: only specific kotowari framework sub-packages are listed here rather than
     * the entire {@code kotowari.} namespace, so that application types under
     * {@code kotowari.example.*} or similar are correctly auto-registered. If new
     * framework sub-packages are added to kotowari they must be listed here explicitly.
     */
    static boolean shouldSkipType(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.")
            || name.startsWith("javax.")
            || name.startsWith("jakarta.")
            || name.startsWith("jdk.")
            || name.startsWith("sun.")
            || name.startsWith("com.sun.")
            || name.startsWith("enkan.")
            || name.startsWith("kotowari.graalvm.")
            || name.startsWith("kotowari.routing.")
            || name.startsWith("kotowari.middleware.")
            || name.startsWith("kotowari.inject.")
            || name.startsWith("kotowari.data.");
    }

    /**
     * Generate a {@code KotowariDispatcher} class with the Class File API.
     *
     * <p>The generated class implements {@link KotowariDispatcherInvoker} and has an instance method:
     * <pre>{@code
     * public Object dispatch(String key, Object controller, Object[] args)
     * }</pre>
     *
     * The method body is an if-chain over the {@code key} string (Controller#action format),
     * each branch casting {@code controller} to the concrete type and calling the action method
     * via {@code invokevirtual}.
     */
    byte[] generateDispatcher(List<RouteEntry> entries) {
        ClassDesc dispatcherDesc = ClassDesc.of("kotowari.graalvm.KotowariDispatcher");
        ClassDesc invokerDesc = ClassDesc.of("kotowari.graalvm.KotowariDispatcherInvoker");
        ClassDesc objectArrayDesc = CD_Object.arrayType();

        return ClassFile.of().build(dispatcherDesc, classBuilder -> {
            classBuilder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            classBuilder.withInterfaceSymbols(invokerDesc);

            // No-arg constructor: public KotowariDispatcher() { super(); }
            classBuilder.withMethod(INIT_NAME,
                    MethodTypeDesc.of(CD_void),
                    ClassFile.ACC_PUBLIC,
                    methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                        codeBuilder.aload(0);
                        codeBuilder.invokespecial(CD_Object, INIT_NAME, MethodTypeDesc.of(CD_void));
                        codeBuilder.return_();
                    }));

            // Instance dispatch method implementing KotowariDispatcherInvoker
            classBuilder.withMethod("dispatch",
                    MethodTypeDesc.of(CD_Object, CD_String, CD_Object, objectArrayDesc),
                    ClassFile.ACC_PUBLIC,
                    methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                        buildDispatchBody(codeBuilder, entries, dispatcherDesc);
                    }));
        });
    }

    private void buildDispatchBody(CodeBuilder code, List<RouteEntry> entries, ClassDesc dispatcherDesc) {
        for (RouteEntry entry : entries) {
            try {
                Class<?> ctrlClass = Class.forName(entry.controllerClassName());
                // Find the action method
                Method actionMethod = null;
                for (Method m : ctrlClass.getDeclaredMethods()) {
                    if (m.getName().equals(entry.actionName()) && Modifier.isPublic(m.getModifiers())) {
                        actionMethod = m;
                        break;
                    }
                }
                if (actionMethod == null) {
                    continue;
                }

                String key = entry.controllerClassName() + "#" + entry.actionName();
                ClassDesc ctrlDesc = ClassDesc.of(entry.controllerClassName());

                // Emit: if (key.equals(arg1)) { return ((CtrlClass)arg2).action(args); }
                // Slot 0 = this, slot 1 = key, slot 2 = controller, slot 3 = args
                var skipLabel = code.newLabel();
                code.aload(1);                              // load key arg
                code.ldc(key);                              // push literal key
                code.invokevirtual(CD_String,
                        "equals",
                        MethodTypeDesc.of(CD_boolean, CD_Object));
                code.ifeq(skipLabel);                       // skip if not equal

                code.aload(2);
                code.checkcast(ctrlDesc);

                // Load arguments from Object[] arg3
                Parameter[] params = actionMethod.getParameters();
                for (int i = 0; i < params.length; i++) {
                    code.aload(3);
                    code.ldc(i);
                    code.aaload();
                    Class<?> paramType = params[i].getType();
                    if (paramType.isPrimitive()) {
                        // unbox: checkcast wrapper, then invoke XxxValue()
                        code.checkcast(wrapperDesc(paramType));
                        emitUnbox(code, paramType);
                    } else {
                        code.checkcast(toClassDesc(paramType));
                    }
                }

                MethodTypeDesc actionDesc = buildMethodTypeDesc(actionMethod);
                code.invokevirtual(ctrlDesc, actionMethod.getName(), actionDesc);

                // Box primitive return or push null for void
                Class<?> returnType = actionMethod.getReturnType();
                if (returnType == void.class) {
                    code.aconst_null();
                } else if (returnType.isPrimitive()) {
                    emitBox(code, returnType);
                }
                code.areturn();

                code.labelBinding(skipLabel);

            } catch (ClassNotFoundException e) {
                // Controller class not on build-time classpath; skip
            }
        }

        // Fallback: throw IllegalArgumentException
        code.new_(ClassDesc.of("java.lang.IllegalArgumentException"));
        code.dup();
        code.aload(1);
        code.invokespecial(ClassDesc.of("java.lang.IllegalArgumentException"),
                INIT_NAME,
                MethodTypeDesc.of(CD_void, CD_String));
        code.athrow();
    }

    private MethodTypeDesc buildMethodTypeDesc(Method method) {
        ClassDesc returnDesc = mapType(method.getReturnType());
        ClassDesc[] paramDescs = new ClassDesc[method.getParameterCount()];
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            paramDescs[i] = mapType(paramTypes[i]);
        }
        return MethodTypeDesc.of(returnDesc, paramDescs);
    }

    private ClassDesc mapType(Class<?> type) {
        if (type == void.class) return CD_void;
        if (type == int.class) return CD_int;
        if (type == long.class) return CD_long;
        if (type == boolean.class) return CD_boolean;
        if (type == double.class) return CD_double;
        if (type == float.class) return CD_float;
        if (type == byte.class) return CD_byte;
        if (type == short.class) return CD_short;
        if (type == char.class) return CD_char;
        return toClassDesc(type);
    }

    /** Convert a reference or array type to {@link ClassDesc} using the descriptor string. */
    private ClassDesc toClassDesc(Class<?> type) {
        // type.descriptorString() works for all types: reference, array, and primitive
        return ClassDesc.ofDescriptor(type.descriptorString());
    }

    /** Return the {@link ClassDesc} of the wrapper type for a primitive. */
    private ClassDesc wrapperDesc(Class<?> primitive) {
        if (primitive == int.class)     return ClassDesc.of("java.lang.Integer");
        if (primitive == long.class)    return ClassDesc.of("java.lang.Long");
        if (primitive == boolean.class) return ClassDesc.of("java.lang.Boolean");
        if (primitive == double.class)  return ClassDesc.of("java.lang.Double");
        if (primitive == float.class)   return ClassDesc.of("java.lang.Float");
        if (primitive == byte.class)    return ClassDesc.of("java.lang.Byte");
        if (primitive == short.class)   return ClassDesc.of("java.lang.Short");
        if (primitive == char.class)    return ClassDesc.of("java.lang.Character");
        throw new IllegalArgumentException("Not a primitive: " + primitive);
    }

    /** Emit an unboxing call (e.g. {@code Integer.intValue()}) for the given primitive type. */
    private void emitUnbox(CodeBuilder code, Class<?> primitive) {
        String method;
        ClassDesc wrapDesc = wrapperDesc(primitive);
        ClassDesc primDesc = mapType(primitive);
        if (primitive == int.class)     method = "intValue";
        else if (primitive == long.class)    method = "longValue";
        else if (primitive == boolean.class) method = "booleanValue";
        else if (primitive == double.class)  method = "doubleValue";
        else if (primitive == float.class)   method = "floatValue";
        else if (primitive == byte.class)    method = "byteValue";
        else if (primitive == short.class)   method = "shortValue";
        else if (primitive == char.class)    method = "charValue";
        else throw new IllegalArgumentException("Not a primitive: " + primitive);
        code.invokevirtual(wrapDesc, method, MethodTypeDesc.of(primDesc));
    }

    /** Emit a boxing call (e.g. {@code Integer.valueOf(int)}) for the given primitive type. */
    private void emitBox(CodeBuilder code, Class<?> primitive) {
        ClassDesc wrapDesc = wrapperDesc(primitive);
        ClassDesc primDesc = mapType(primitive);
        code.invokestatic(wrapDesc, "valueOf", MethodTypeDesc.of(wrapDesc, primDesc));
    }

    private Class<?> defineDispatcherClass(byte[] bytes) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.defineClass(bytes);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to define KotowariDispatcher", e);
        }
    }

    record RouteEntry(String controllerClassName, String actionName) {}
}
