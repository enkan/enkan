package kotowari.inject;

import enkan.web.data.HttpRequest;

/**
 * A {@link ParameterInjector} whose applicability depends on runtime request
 * state, not just the parameter type.
 *
 * <p>Standard injectors determine applicability from the parameter type alone
 * (via {@link #isApplicable(Class)}), which allows static resolution at
 * construction time. This subinterface adds
 * {@link #isApplicable(Class, HttpRequest)} for injectors that need to inspect
 * the actual request (e.g. checking the deserialized body type).
 *
 * @param <T> the type of the injected object
 * @author kawasima
 */
public interface RuntimeParameterInjector<T> extends ParameterInjector<T> {
    /**
     * Whether this injector can inject the given type in the context of a
     * specific request.
     *
     * @param type    the class of a parameter
     * @param request the current request object
     * @return true if the injector can inject the given type for this request
     */
    boolean isApplicable(Class<?> type, HttpRequest request);
}
