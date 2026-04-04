package kotowari.inject;

import enkan.web.data.HttpRequest;

/**
 * Inject object to a controller method.
 *
 * <p>Applicability is determined solely by the parameter type via
 * {@link #isApplicable(Class)}, enabling static resolution at construction
 * time. Injectors that require runtime request state should implement
 * {@link RuntimeParameterInjector} instead.
 *
 * @param <T> The type of Inject object.
 * @author kawasima
 */
public interface ParameterInjector<T> {
    /**
     * Get the name of the injector.
     *
     * @return the name of the injector
     */
    String getName();

    /**
     * Whether the injector can inject the given parameter type.
     *
     * @param type the class of a parameter
     * @return true if the injector can inject the given type.
     */
    boolean isApplicable(Class<?> type);

    /**
     * Get a object for injecting.
     *
     * @param request the request object
     * @return an object for injection
     */
    T getInjectObject(HttpRequest request);
}
