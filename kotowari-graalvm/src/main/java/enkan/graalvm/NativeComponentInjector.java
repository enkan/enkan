package enkan.graalvm;

import enkan.component.SystemComponent;
import enkan.system.inject.ComponentInjector;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link ComponentInjector} subclass that uses pre-generated {@link ComponentBinder}
 * instances for component creation instead of reflection.
 *
 * <p>For each component class registered in {@link NativeComponentRegistry}, this injector
 * delegates to the generated binder (direct field writes, zero reflection at runtime) for
 * {@code @Named @Inject} fields, then falls back to {@link #inject} for any remaining
 * unnamed {@code @Inject} fields and invokes {@code @PostConstruct}.
 * For any class without a registered binder, it falls back entirely to the standard
 * reflection-based {@code ComponentInjector}.
 *
 * <p><b>Reflection requirement:</b> unnamed {@code @Inject} fields (those annotated with
 * {@code @Inject} but <em>not</em> with {@code @Named}) are resolved by
 * {@link ComponentInjector#injectField} which calls {@code Field.trySetAccessible()}.
 * Because {@link #collectUnnamedInjectFields} walks the full superclass hierarchy,
 * this requirement applies to every <em>declaring class</em> of such fields, not only
 * the concrete component class.  Each such class must have
 * {@code "allDeclaredFields": true} in its {@code reflect-config.json} entry,
 * or be registered via {@code EnkanFeature} with
 * {@code RuntimeReflection.registerAllFields(componentClass)}.
 * Components that rely only on {@code @Named @Inject} fields (the common case for
 * named component dependencies) require no additional reflection registration.
 */
public class NativeComponentInjector extends ComponentInjector {
    private final Map<String, SystemComponent<?>> components;

    public NativeComponentInjector(Map<String, SystemComponent<?>> components) {
        super(components);
        this.components = components;
    }

    @Override
    public <T> T newInstance(Class<T> clazz) {
        ComponentBinder<T> binder = NativeComponentRegistry.get(clazz);
        if (binder == null) {
            return super.newInstance(clazz);
        }

        // binder handles @Named @Inject fields via direct putfield (no reflection).
        // Inject any remaining unnamed @Inject fields via the reflection fallback,
        // then call @PostConstruct.
        T instance = binder.bind(components);
        collectUnnamedInjectFields(clazz)
                .forEach(f -> injectField(instance, f));
        postConstruct(instance);
        return instance;
    }

    private List<Field> collectUnnamedInjectFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class
                && !current.getName().equals("enkan.component.SystemComponent")) {
            for (Field f : current.getDeclaredFields()) {
                if (f.getAnnotation(Inject.class) != null && f.getAnnotation(Named.class) == null) {
                    result.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }
}
