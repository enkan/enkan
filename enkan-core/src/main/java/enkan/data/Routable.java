package enkan.data;

import java.lang.reflect.Method;

/**
 * Mixin interface that stores the resolved controller class and action method
 * on the request. Applied by {@code RoutingMiddleware} after route recognition.
 *
 * @author kawasima
 */
public interface Routable extends Extendable {

    /**
     * Returns the controller class resolved by routing.
     *
     * @return the controller class, or {@code null} if not yet resolved
     */
    default Class<?> getControllerClass() {
        return getExtension("controllerClass");
    }

    /**
     * Sets the controller class for this request.
     *
     * @param controllerClass the resolved controller class
     */
    default void setControllerClass(Class<?> controllerClass) {
        setExtension("controllerClass", controllerClass);
    }

    /**
     * Returns the controller action method resolved by routing.
     *
     * @return the action method, or {@code null} if not yet resolved
     */
    default Method getControllerMethod() {
        return getExtension("controllerMethod");
    }

    /**
     * Sets the controller action method for this request.
     *
     * @param controllerMethod the resolved action method
     */
    default void setControllerMethod(Method controllerMethod) {
        setExtension("controllerMethod", controllerMethod);
    }

    /**
     * Returns the fully-qualified controller method name (e.g. {@code "com.example.Ctrl#action"}).
     *
     * @return the controller method name, or {@code null} if not yet resolved
     */
    default String getControllerMethodName() {
        return getExtension("controllerMethodName");
    }

    /**
     * Sets the fully-qualified controller method name.
     *
     * @param name the controller method name
     */
    default void setControllerMethodName(String name) {
        setExtension("controllerMethodName", name);
    }
}
