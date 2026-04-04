package enkan.graalvm;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnkanFeatureTest {

    private final EnkanFeature feature = new EnkanFeature();

    @AfterEach
    void cleanRegistry() {
        NativeComponentRegistry.unregister(DerivedComponent.class);
    }

    // --- test fixtures ---

    static class DependencyComponent extends SystemComponent<DependencyComponent> {
        @Override
        protected ComponentLifecycle<DependencyComponent> lifecycle() {
            return new ComponentLifecycle<>() {
                @Override public void start(DependencyComponent c) {}
                @Override public void stop(DependencyComponent c) {}
            };
        }
    }

    static class SimpleComponent extends SystemComponent<SimpleComponent> {
        @Inject
        @Named("dep")
        DependencyComponent dep;

        @Override
        protected ComponentLifecycle<SimpleComponent> lifecycle() {
            return new ComponentLifecycle<>() {
                @Override public void start(SimpleComponent c) {}
                @Override public void stop(SimpleComponent c) {}
            };
        }
    }

    static class NoInjectComponent extends SystemComponent<NoInjectComponent> {
        String value = "original";

        @Override
        protected ComponentLifecycle<NoInjectComponent> lifecycle() {
            return new ComponentLifecycle<>() {
                @Override public void start(NoInjectComponent c) {}
                @Override public void stop(NoInjectComponent c) {}
            };
        }
    }

    static class UnnamedInjectComponent extends SystemComponent<UnnamedInjectComponent> {
        @Inject
        @SuppressWarnings("unused")
        DependencyComponent dep;  // no @Named — should be excluded from binder

        @Override
        protected ComponentLifecycle<UnnamedInjectComponent> lifecycle() {
            return new ComponentLifecycle<>() {
                @Override public void start(UnnamedInjectComponent c) {}
                @Override public void stop(UnnamedInjectComponent c) {}
            };
        }
    }

    @Test
    void generateBinderInjectsNamedField() throws Exception {
        byte[] bytes = feature.generateBinderBytecode(SimpleComponent.class);
        assertThat(bytes).isNotEmpty();

        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                SimpleComponent.class, MethodHandles.lookup());
        MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClass(
                bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);

        @SuppressWarnings("unchecked")
        ComponentBinder<SimpleComponent> binder =
                (ComponentBinder<SimpleComponent>) hiddenLookup.lookupClass()
                        .getConstructor().newInstance();

        DependencyComponent dep = new DependencyComponent();
        Map<String, SystemComponent<?>> components = new HashMap<>();
        components.put("dep", dep);

        SimpleComponent result = binder.bind(components);

        assertThat(result).isNotNull();
        assertThat(result.dep).isSameAs(dep);
    }

    @Test
    void generateBinderWithNoNamedFieldsReturnsInstance() throws Exception {
        byte[] bytes = feature.generateBinderBytecode(NoInjectComponent.class);
        assertThat(bytes).isNotEmpty();

        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                NoInjectComponent.class, MethodHandles.lookup());
        MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClass(
                bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);

        @SuppressWarnings("unchecked")
        ComponentBinder<NoInjectComponent> binder =
                (ComponentBinder<NoInjectComponent>) hiddenLookup.lookupClass()
                        .getConstructor().newInstance();

        NoInjectComponent result = binder.bind(Map.of());

        assertThat(result).isNotNull();
        assertThat(result.value).isEqualTo("original");
    }

    @Test
    void collectNamedInjectFieldsFindsAnnotatedFields() {
        List<java.lang.reflect.Field> fields = feature.collectNamedInjectFields(SimpleComponent.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getName()).isEqualTo("dep");
    }

    @Test
    void collectNamedInjectFieldsIgnoresUnnamedInjectFields() {
        List<java.lang.reflect.Field> fields = feature.collectNamedInjectFields(UnnamedInjectComponent.class);

        assertThat(fields).isEmpty();
    }

    // --- fixtures for superclass hierarchy tests ---

    /**
     * Intermediate SystemComponent subclass that carries an unnamed @Inject field.
     * DerivedComponent extends this to simulate the superclass-hierarchy case.
     */
    static class MiddleComponent extends SystemComponent<MiddleComponent> {
        @Inject
        @SuppressWarnings("unused")
        DependencyComponent unnamedDep;  // no @Named — resolved by type

        @Override
        protected ComponentLifecycle<MiddleComponent> lifecycle() {
            return new ComponentLifecycle<>() {
                @Override public void start(MiddleComponent c) {}
                @Override public void stop(MiddleComponent c) {}
            };
        }
    }

    /** Concrete component whose unnamed @Inject field lives in the superclass. */
    static class DerivedComponent extends MiddleComponent {}

    @Test
    void collectNamedInjectFieldsIgnoresUnnamedFieldInSuperclass() {
        List<java.lang.reflect.Field> fields =
                feature.collectNamedInjectFields(DerivedComponent.class);

        assertThat(fields).isEmpty();
    }

    @Test
    void newInstanceInjectsUnnamedFieldFromSuperclass() throws Exception {
        byte[] bytes = feature.generateBinderBytecode(DerivedComponent.class);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                DerivedComponent.class, MethodHandles.lookup());
        MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClass(
                bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);
        @SuppressWarnings("unchecked")
        ComponentBinder<DerivedComponent> binder =
                (ComponentBinder<DerivedComponent>) hiddenLookup.lookupClass()
                        .getConstructor().newInstance();

        DependencyComponent dep = new DependencyComponent();
        Map<String, SystemComponent<?>> components = new HashMap<>();
        components.put("dep", dep);

        NativeComponentRegistry.register(DerivedComponent.class, binder);
        NativeComponentInjector injector = new NativeComponentInjector(components);

        DerivedComponent result = injector.newInstance(DerivedComponent.class);

        assertThat(result).isNotNull();
        assertThat(result.unnamedDep)
                .as("unnamed @Inject field declared in superclass must be injected via hierarchy walk")
                .isSameAs(dep);
    }
}
