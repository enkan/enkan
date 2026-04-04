package kotowari.graalvm;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.web.data.HttpRequest;
import enkan.data.Routable;
import enkan.exception.MisconfigurationException;
import enkan.system.inject.ComponentInjector;
import kotowari.inject.ParameterInjector;
import kotowari.inject.parameter.BodySerializableInjector;
import kotowari.util.ParameterUtils;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static enkan.util.ReflectionUtils.tryReflection;

/**
 * A reflection-free alternative to {@code ControllerInvokerMiddleware} for use in
 * GraalVM native images.
 *
 * <p>Instead of {@link java.lang.invoke.LambdaMetafactory}, dispatch is delegated to the
 * build-time-generated {@code KotowariDispatcher.dispatch(String, Object, Object[])} method
 * which contains a direct {@code invokevirtual} call for each registered route.
 *
 * <p>The dispatch key is {@code "FQCN#actionName"} set on the request by
 * {@link kotowari.middleware.RoutingMiddleware} via {@link Routable#setControllerMethodName}.
 *
 * <p>Parameter injectors and controller instances are cached exactly as in the JVM version
 * to avoid any per-request overhead.
 */
@enkan.annotation.Middleware(name = "controllerInvoker", dependencies = "params")
public class NativeControllerInvokerMiddleware<RES> implements Middleware<HttpRequest, RES, Void, Void> {
    private final ComponentInjector componentInjector;
    private final Map<Class<?>, Object> controllerCache = new ConcurrentHashMap<>();
    private final Map<String, ParameterInjector<?>[]> injectorCache = new ConcurrentHashMap<>();
    private static final ParameterInjector<?> BODY_SERIALIZABLE_INJECTOR = new BodySerializableInjector<>();
    private List<ParameterInjector<?>> parameterInjectors;

    public NativeControllerInvokerMiddleware(ComponentInjector componentInjector) {
        this.componentInjector = componentInjector;
    }

    @PostConstruct
    protected void setupParameterInjectors() {
        if (parameterInjectors == null) {
            parameterInjectors = ParameterUtils.getDefaultParameterInjectors();
        }
    }

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

    protected Object[] createArguments(HttpRequest request, ParameterInjector<?>[] injectors) {
        Object[] arguments = new Object[injectors.length];
        for (int i = 0; i < injectors.length; i++) {
            arguments[i] = injectors[i].getInjectObject(request);
        }
        return arguments;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <NNREQ, NNRES> RES handle(HttpRequest request, MiddlewareChain<Void, Void, NNREQ, NNRES> next) {
        if (!(request instanceof Routable routable)) {
            throw new MisconfigurationException("core.MISSING_IMPLEMENTATION", Routable.class);
        }

        String methodName = routable.getControllerMethodName();
        if (methodName == null) {
            throw new MisconfigurationException("kotowari.CONTROLLER_METHOD_NOT_FOUND",
                    "NativeControllerInvokerMiddleware");
        }

        Method controllerMethod = routable.getControllerMethod();
        if (controllerMethod == null) {
            throw new MisconfigurationException("kotowari.CONTROLLER_METHOD_NOT_FOUND",
                    "NativeControllerInvokerMiddleware");
        }

        Class<?> controllerClass = controllerMethod.getDeclaringClass();
        Object controller = controllerCache.computeIfAbsent(controllerClass, c ->
                componentInjector != null ? componentInjector.newInstance(c)
                        : tryReflection(() -> c.getConstructor().newInstance()));

        ParameterInjector<?>[] injectors = injectorCache.computeIfAbsent(methodName,
                k -> resolveInjectors(controllerMethod));
        Object[] arguments = createArguments(request, injectors);

        KotowariDispatcherInvoker invoker = NativeDispatcherRegistry.get();
        if (invoker == null) {
            throw new MisconfigurationException("kotowari.CONTROLLER_METHOD_NOT_FOUND",
                    "KotowariDispatcher not registered");
        }
        return (RES) invoker.dispatch(methodName, controller, arguments);
    }

    public void setParameterInjectors(List<ParameterInjector<?>> parameterInjectors) {
        this.parameterInjectors = parameterInjectors;
    }
}
