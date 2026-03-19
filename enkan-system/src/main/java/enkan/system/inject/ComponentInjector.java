package enkan.system.inject;

import enkan.component.SystemComponent;
import enkan.exception.MisconfigurationException;
import enkan.exception.UnreachableException;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import static enkan.util.ReflectionUtils.tryReflection;
import static enkan.util.SearchUtils.levenshteinDistance;

/**
 * @author kawasima
 */
public class ComponentInjector {
    private final Map<String, SystemComponent<?>> components;

    public ComponentInjector(Map<String, SystemComponent<?>> components) {
        this.components = components;
    }

    private boolean isInjectTarget(Field f) {
        return f.getAnnotation(Inject.class) != null;
    }

    /**
     * Set a value to a field of an object.
     * @param target an injection target object
     * @param f      an injection target field
     * @param value  an injecting value
     */
    private void setValueToField(Object target, Field f, Object value) {
        f.setAccessible(true);
        try {
            f.set(target, value);
        } catch (IllegalAccessException e) {
            throw new UnreachableException(e);
        }
    }

    protected void injectField(Object target, Field f) {
        Named named = f.getAnnotation(Named.class);
        if (named != null) {
            String name = named.value();
            SystemComponent<?> component = components.get(name);
            if (component != null) {
                setValueToField(target, f, component);
            } else {
                Optional<String> correctName = components.entrySet().stream()
                        .filter(c -> f.getType().isAssignableFrom(c.getValue().getClass()))
                        .map(Map.Entry::getKey)
                        .min(Comparator.comparing(n -> levenshteinDistance(n, name)));
                if (correctName.isPresent()) {
                    throw new MisconfigurationException("core.INJECT_WRONG_NAMED_COMPONENT", name, correctName.get());
                } else {
                    throw new MisconfigurationException("core.INJECT_WRONG_TYPE_COMPONENT", name, f.getType());
                }
            }
        } else {
            components.values().stream()
                    .filter(component -> f.getType().isAssignableFrom(component.getClass()))
                    .findFirst()
                    .ifPresent(c -> setValueToField(target, f, c));
        }
    }

    protected <T> void postConstruct(T target) {
        Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(m -> m.getAnnotation(PostConstruct.class) != null)
                .findFirst()
                .ifPresent(m -> tryReflection(
                        () -> {
                            m.setAccessible(true);
                            return m.invoke(target);
                        })
                );
    }

    public <T> T inject(T target) {
        Arrays.stream(target.getClass().getDeclaredFields())
                .filter(this::isInjectTarget)
                .forEach(f -> injectField(target, f));
        postConstruct(target);
        return target;
    }

    /**
     * Create a new instance of the given class using constructor injection if available.
     *
     * <p>Constructor selection strategy (similar to Spring):
     * <ol>
     *   <li>If a constructor is annotated with {@code @Inject}, use it.</li>
     *   <li>If there is exactly one constructor with parameters, use it (implicit constructor injection).</li>
     *   <li>Otherwise, use the default constructor and apply field injection.</li>
     * </ol>
     *
     * <p>When constructor injection is used, {@code @Inject} fields are also injected
     * and {@code @PostConstruct} methods are called.
     *
     * @param clazz the class to instantiate
     * @param <T>   the type of the class
     * @return a new instance with dependencies injected
     */
    public <T> T newInstance(Class<T> clazz) {
        Constructor<T> constructor = findInjectionConstructor(clazz);
        if (constructor != null) {
            constructor.setAccessible(true);
            Object[] args = resolveConstructorArgs(constructor);
            T instance = tryReflection(() -> constructor.newInstance(args));
            return inject(instance);
        }
        // Fallback: default constructor + field injection
        T instance = tryReflection(() -> clazz.getConstructor().newInstance());
        return inject(instance);
    }

    /**
     * Find the constructor to use for injection.
     *
     * <ol>
     *   <li>If exactly one constructor has {@code @Inject}, use it.</li>
     *   <li>If multiple constructors have {@code @Inject}, throw an error.</li>
     *   <li>If no {@code @Inject} constructor exists but exactly one constructor
     *       with parameters exists, use it (implicit injection, like Spring).</li>
     *   <li>Otherwise, return {@code null} (use default constructor).</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private <T> Constructor<T> findInjectionConstructor(Class<T> clazz) {
        Constructor<?>[] allConstructors = clazz.getDeclaredConstructors();

        Constructor<?>[] injectConstructors = Arrays.stream(allConstructors)
                .filter(c -> c.getAnnotation(Inject.class) != null)
                .toArray(Constructor<?>[]::new);

        if (injectConstructors.length == 1) {
            return (Constructor<T>) injectConstructors[0];
        }
        if (injectConstructors.length > 1) {
            throw new MisconfigurationException("core.MULTIPLE_INJECT_CONSTRUCTORS", clazz.getName());
        }

        // No @Inject constructor — check for implicit single-constructor injection
        Constructor<?>[] paramConstructors = Arrays.stream(allConstructors)
                .filter(c -> c.getParameterCount() > 0)
                .toArray(Constructor<?>[]::new);

        if (paramConstructors.length == 1) {
            return (Constructor<T>) paramConstructors[0];
        }

        return null;
    }

    private Object[] resolveConstructorArgs(Constructor<?> constructor) {
        java.lang.reflect.Parameter[] params = constructor.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            Named named = params[i].getAnnotation(Named.class);
            if (named != null) {
                SystemComponent<?> component = components.get(named.value());
                if (component == null) {
                    Optional<String> suggestion = suggestName(type, named.value());
                    if (suggestion.isPresent()) {
                        throw new MisconfigurationException("core.INJECT_WRONG_NAMED_COMPONENT",
                                named.value(), suggestion.get());
                    } else {
                        throw new MisconfigurationException("core.INJECT_MISSING_COMPONENT",
                                type.getName(), constructor.getDeclaringClass().getName());
                    }
                }
                if (!type.isAssignableFrom(component.getClass())) {
                    throw new MisconfigurationException("core.INJECT_WRONG_TYPE_COMPONENT",
                            named.value(), type);
                }
                args[i] = component;
            } else {
                args[i] = components.values().stream()
                        .filter(component -> type.isAssignableFrom(component.getClass()))
                        .findFirst()
                        .orElseThrow(() -> new MisconfigurationException(
                                "core.INJECT_MISSING_COMPONENT", type.getName(),
                                constructor.getDeclaringClass().getName()));
            }
        }
        return args;
    }

    private Optional<String> suggestName(Class<?> type, String wrongName) {
        return components.entrySet().stream()
                .filter(c -> type.isAssignableFrom(c.getValue().getClass()))
                .map(Map.Entry::getKey)
                .min(Comparator.comparing(n -> levenshteinDistance(n, wrongName)));
    }
}
