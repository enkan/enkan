package kotowari.middleware;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.data.HttpRequest;
import enkan.data.Routable;
import enkan.exception.MisconfigurationException;
import enkan.system.inject.ComponentInjector;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.*;
import kotowari.util.ParameterUtils;

import jakarta.annotation.PostConstruct;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static enkan.util.ReflectionUtils.*;

/**
 * Kotowari endpoint.
 * This middleware inject arguments to controller method.
 *
 * <ul>
 *     <li>HttpRequest - Inject a request object.</li>
 *     <li>Map - Inject parsed request parameters.</li>
 *     <li>JavaBean - Inject form.</li>
 * </ul>
 *
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "controllerInvoker", dependencies = "params")
public class ControllerInvokerMiddleware<RES> implements Middleware<HttpRequest, RES, Void, Void> {
    private static final Logger LOG = Logger.getLogger(ControllerInvokerMiddleware.class.getName());
    private final Map<Class<?>, Object> controllerCache = new ConcurrentHashMap<>();
    private final Map<Method, MethodInvocation> methodCache = new ConcurrentHashMap<>();
    private final ComponentInjector componentInjector;
    private static final ParameterInjector<?> BODY_SERIALIZABLE_INJECTOR = new BodySerializableInjector<>();
    private List<ParameterInjector<?>> parameterInjectors;

    // Fixed-arity functional interfaces so LambdaMetafactory generates
    // direct invokevirtual/invokestatic bytecode (JIT-inlinable).
    @FunctionalInterface private interface Invoker0 { Object invoke(Object c) throws Throwable; }
    @FunctionalInterface private interface Invoker1 { Object invoke(Object c, Object a0) throws Throwable; }
    @FunctionalInterface private interface Invoker2 { Object invoke(Object c, Object a0, Object a1) throws Throwable; }
    @FunctionalInterface private interface Invoker3 { Object invoke(Object c, Object a0, Object a1, Object a2) throws Throwable; }
    @FunctionalInterface private interface Invoker4 { Object invoke(Object c, Object a0, Object a1, Object a2, Object a3) throws Throwable; }
    @FunctionalInterface private interface Invoker5 { Object invoke(Object c, Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable; }
    @FunctionalInterface private interface Invoker6 { Object invoke(Object c, Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable; }
    @FunctionalInterface private interface Invoker7 { Object invoke(Object c, Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable; }

    private static final Class<?>[] INVOKER_CLASSES = {
            Invoker0.class, Invoker1.class, Invoker2.class, Invoker3.class,
            Invoker4.class, Invoker5.class, Invoker6.class, Invoker7.class
    };

    /**
     * Unified invoker that adapts fixed-arity invokers to a common
     * {@code (Object, Object[]) -> Object} shape.
     */
    @FunctionalInterface
    private interface ControllerInvoker {
        Object invoke(Object controller, Object[] args) throws Throwable;
    }

    /**
     * Cached per-method invocation data: resolved injectors and the
     * LambdaMetafactory-generated invoker.
     */
    private record MethodInvocation(ParameterInjector<?>[] injectors, ControllerInvoker invoker) {}

    public ControllerInvokerMiddleware(ComponentInjector componentInjector) {
        this.componentInjector = componentInjector;
    }

    @PostConstruct
    protected void setupParameterInjectors() {
        if (parameterInjectors == null) {
            parameterInjectors = ParameterUtils.getDefaultParameterInjectors();
        }
    }

    /**
     * Resolve injectors for each parameter of the given method by type only.
     *
     * <p>{@link ParameterInjector#isApplicable(Class)} determines applicability
     * solely from the parameter type, enabling static resolution at construction
     * time. Injectors that require runtime request state implement
     * {@link kotowari.inject.RuntimeParameterInjector} and are used as fallbacks.
     */
    private ParameterInjector<?>[] resolveInjectors(Method method) {
        Parameter[] parameters = method.getParameters();
        ParameterInjector<?>[] injectors = new ParameterInjector<?>[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();
            injectors[i] = parameterInjectors.stream()
                    .filter(injector -> injector.isApplicable(type))
                    .findFirst()
                    .orElse(BODY_SERIALIZABLE_INJECTOR);
        }
        return injectors;
    }

    /**
     * Build a {@link ControllerInvoker} for the given method using
     * {@link LambdaMetafactory} with a fixed-arity functional interface.
     *
     * <p>For methods with 0–7 parameters whose declaring class shares the
     * same {@link ClassLoader} as this middleware, a typed {@code InvokerN}
     * interface is used so the generated class emits direct
     * {@code invokevirtual} bytecode that the JIT can inline.
     *
     * <p>When ClassLoaders differ (e.g. hot-reload via {@code ConfigurationLoader}),
     * or for methods with 8+ parameters, falls back to {@link MethodHandle}
     * with {@code asSpreader}.
     *
     * @param method the controller method to build an invoker for
     * @return a fast invoker
     */
    private ControllerInvoker buildInvoker(Method method) {
        int paramCount = method.getParameterCount();
        ClassLoader controllerCL = method.getDeclaringClass().getClassLoader();
        ClassLoader middlewareCL = ControllerInvokerMiddleware.class.getClassLoader();
        boolean sameClassLoader = Objects.equals(controllerCL, middlewareCL);

        if (paramCount < INVOKER_CLASSES.length && sameClassLoader) {
            try {
                return buildLambdaInvoker(method, paramCount);
            } catch (Throwable e) {
                LOG.log(Level.WARNING, e,
                        () -> "LambdaMetafactory failed for " + method + "; falling back to MethodHandle");
                return buildMethodHandleFallback(method);
            }
        } else {
            if (!sameClassLoader) {
                LOG.log(Level.FINE,
                        () -> "ClassLoader mismatch for " + method + "; using MethodHandle fallback");
            }
            return buildMethodHandleFallback(method);
        }
    }

    /**
     * Generate a {@link ControllerInvoker} via {@link LambdaMetafactory}
     * using a fixed-arity {@code InvokerN} interface.
     *
     * <p>This method is only called when the controller's ClassLoader matches
     * the middleware's ClassLoader, so using concrete types in
     * {@code instantiatedType} is safe and enables the JIT to emit direct
     * {@code invokevirtual}/{@code checkcast} bytecode.
     */
    private ControllerInvoker buildLambdaInvoker(Method method, int paramCount) throws Throwable {
        MethodHandles.Lookup controllerLookup = MethodHandles.privateLookupIn(
                method.getDeclaringClass(), MethodHandles.lookup());
        MethodHandle handle = controllerLookup.unreflect(method);

        Class<?> invokerClass = INVOKER_CLASSES[paramCount];
        MethodType erasedType = MethodType.genericMethodType(1 + paramCount);
        // Concrete types enable direct invokevirtual + checkcast in the
        // generated class.  Safe because buildInvoker() only calls this path
        // when ClassLoaders match.
        MethodType instantiatedType = handle.type();

        // Use MethodHandles.lookup() (this class context) for metafactory so it
        // can see the private InvokerN interfaces.
        MethodHandles.Lookup factoryLookup = MethodHandles.lookup();
        CallSite callSite = LambdaMetafactory.metafactory(
                factoryLookup,
                "invoke",
                MethodType.methodType(invokerClass),
                erasedType,            // erased SAM signature
                handle,                // direct MethodHandle
                instantiatedType       // concrete types → direct invokevirtual
        );

        // Extract the typed invoker and wrap in a ControllerInvoker that spreads Object[]
        return switch (paramCount) {
            case 0 -> {
                Invoker0 fn = (Invoker0) callSite.getTarget().invokeExact();
                yield (c, args) -> fn.invoke(c);
            }
            case 1 -> {
                Invoker1 fn = (Invoker1) callSite.getTarget().invokeExact();
                yield (c, args) -> fn.invoke(c, args[0]);
            }
            case 2 -> {
                Invoker2 fn = (Invoker2) callSite.getTarget().invokeExact();
                yield (c, args) -> fn.invoke(c, args[0], args[1]);
            }
            case 3 -> {
                Invoker3 fn = (Invoker3) callSite.getTarget().invokeExact();
                yield (c, args) -> fn.invoke(c, args[0], args[1], args[2]);
            }
            case 4 -> {
                Invoker4 fn = (Invoker4) callSite.getTarget().invokeExact();
                yield (c, args) -> fn.invoke(c, args[0], args[1], args[2], args[3]);
            }
            case 5 -> {
                Invoker5 fn = (Invoker5) callSite.getTarget().invokeExact();
                yield (c, args) -> fn.invoke(c, args[0], args[1], args[2], args[3], args[4]);
            }
            case 6 -> {
                Invoker6 fn = (Invoker6) callSite.getTarget().invokeExact();
                yield (c, args) -> fn.invoke(c, args[0], args[1], args[2], args[3], args[4], args[5]);
            }
            case 7 -> {
                Invoker7 fn = (Invoker7) callSite.getTarget().invokeExact();
                yield (c, args) -> fn.invoke(c, args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
            }
            default -> throw new AssertionError("Unreachable: paramCount=" + paramCount);
        };
    }

    /**
     * Fallback invoker using {@link MethodHandle#invoke} with spreader.
     * Used for methods with 8+ parameters or when LambdaMetafactory fails.
     */
    private ControllerInvoker buildMethodHandleFallback(Method method) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    method.getDeclaringClass(), MethodHandles.lookup());
            MethodHandle handle = lookup.unreflect(method);
            int paramCount = method.getParameterCount();
            MethodHandle spread = handle
                    .asType(MethodType.genericMethodType(1 + paramCount))
                    .asSpreader(Object[].class, paramCount);
            return spread::invoke;
        } catch (IllegalAccessException e) {
            throw new MisconfigurationException("core.ILLEGAL_ACCESS", e.getMessage(), e);
        }
    }

    private MethodInvocation resolveMethod(Method method) {
        return new MethodInvocation(resolveInjectors(method), buildInvoker(method));
    }

    /**
     * Create arguments for controller method.
     *
     * @param request   the request object
     * @param injectors the cached injectors for the method parameters
     * @return arguments for controller method
     */
    protected Object[] createArguments(HttpRequest request, ParameterInjector<?>[] injectors) {
        Object[] arguments = new Object[injectors.length];
        for (int i = 0; i < injectors.length; i++) {
            arguments[i] = injectors[i].getInjectObject(request);
        }
        return arguments;
    }

    /**
     * Invokes the controller method resolved by the routing middleware.
     * Parameter injection is performed using the registered {@link ParameterInjector}s,
     * and the controller instance is cached for reuse across requests.
     *
     * @param request the incoming HTTP request (must implement {@link Routable})
     * @param next    the remaining middleware chain (unused; this is the terminal middleware)
     * @return the value returned by the controller method
     * @throws MisconfigurationException if the request is not {@link Routable} or the controller method is null
     */
    @SuppressWarnings("unchecked")
    @Override
    public <NNREQ, NNRES> RES handle(HttpRequest request, MiddlewareChain<Void, Void, NNREQ, NNRES> next) {
        if (request instanceof Routable routable) {
            Method controllerMethod = routable.getControllerMethod();
            if (controllerMethod == null) {
                throw new MisconfigurationException("kotowari.CONTROLLER_METHOD_NOT_FOUND",
                        "ControllerInvokerMiddleware");
            }
            Class<?> controllerClass = controllerMethod.getDeclaringClass();

            Object controller = controllerCache.computeIfAbsent(controllerClass, c ->
                    componentInjector != null ? componentInjector.newInstance(c)
                            : tryReflection(() -> c.getConstructor().newInstance()));

            MethodInvocation invocation = methodCache.computeIfAbsent(
                    controllerMethod, this::resolveMethod);
            Object[] arguments = createArguments(request, invocation.injectors());

            try {
                return (RES) invocation.invoker().invoke(controller, arguments);
            } catch (Error | RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        } else {
            throw new MisconfigurationException("kotowari.MISSING_IMPLEMENTATION", Routable.class);
        }
    }

    public void setParameterInjectors(List<ParameterInjector<?>> parameterInjectors) {
        this.parameterInjectors = parameterInjectors;
    }
}
