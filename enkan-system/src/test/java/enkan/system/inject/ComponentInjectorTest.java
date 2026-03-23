package enkan.system.inject;

import enkan.component.ComponentLifecycle;
import enkan.component.SystemComponent;
import enkan.exception.MisconfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
public class ComponentInjectorTest {
    private Map<String, SystemComponent<?>> componentMap;

    @BeforeEach
    public void setup() {
        componentMap = new HashMap<>();
    }

    @Test
    public void test() {
        componentMap.put("myNameIsA", new TestComponent("A"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        InjectTarget1 target1 = new InjectTarget1();
        injector.inject(target1);
        assertThat(target1.tc.getId()).isEqualTo("A");
    }

    @Test
    public void testNamedInject() {
        componentMap.put("myNameIsA", new TestComponent("A"));
        componentMap.put("myNameIsB", new TestComponent("B"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        InjectTarget2 target2 = new InjectTarget2();
        injector.inject(target2);
        assertThat(target2.tc.getId()).isEqualTo("B");
    }

    @Test
    public void constructorInjection() {
        componentMap.put("myNameIsA", new TestComponent("A"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        ConstructorInjectionTarget target = injector.newInstance(ConstructorInjectionTarget.class);
        assertThat(target.tc.getId()).isEqualTo("A");
    }

    @Test
    public void namedConstructorInjection() {
        componentMap.put("myNameIsA", new TestComponent("A"));
        componentMap.put("myNameIsB", new TestComponent("B"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        NamedConstructorInjectionTarget target = injector.newInstance(NamedConstructorInjectionTarget.class);
        assertThat(target.tc.getId()).isEqualTo("B");
    }

    @Test
    public void fallbackToFieldInjectionWhenNoInjectConstructor() {
        componentMap.put("myNameIsA", new TestComponent("A"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        NoInjectConstructorTarget target = injector.newInstance(NoInjectConstructorTarget.class);
        assertThat(target.tc.getId()).isEqualTo("A");
    }

    @Test
    public void implicitConstructorInjectionWithoutAnnotation() {
        componentMap.put("myNameIsA", new TestComponent("A"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        ImplicitConstructorTarget target = injector.newInstance(ImplicitConstructorTarget.class);
        assertThat(target.tc.getId()).isEqualTo("A");
    }

    @Test
    public void constructorInjectionAlsoInjectsFields() {
        componentMap.put("myNameIsA", new TestComponent("A"));
        componentMap.put("myNameIsB", new TestComponent("B"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        MixedInjectionTarget target = injector.newInstance(MixedInjectionTarget.class);
        assertThat(target.constructorComponent.getId()).isEqualTo("A");
        assertThat(target.fieldComponent.getId()).isNotNull();
    }

    @Test
    public void multipleInjectConstructorsThrows() {
        componentMap.put("myNameIsA", new TestComponent("A"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        assertThatThrownBy(() -> injector.newInstance(MultipleInjectConstructorTarget.class))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    public void missingComponentThrows() {
        // No components registered
        ComponentInjector injector = new ComponentInjector(componentMap);
        assertThatThrownBy(() -> injector.newInstance(ConstructorInjectionTarget.class))
                .isInstanceOf(MisconfigurationException.class);
    }

    @Test
    public void wrongNamedInject() {
        componentMap.put("myNameIsAAA", new TestComponent("A"));
        componentMap.put("MyNameIsB", new TestComponent("B"));

        ComponentInjector injector = new ComponentInjector(componentMap);
        InjectTarget2 target2 = new InjectTarget2();
        try {
            injector.inject(target2);
            fail("MisconfigurationException will occur");
        } catch (MisconfigurationException ex) {
            assertThat(ex.getSolution().contains("MyNameIsB")).isTrue();
        }
    }

    private static class InjectTarget1 {
        @Inject
        TestComponent tc;
    }

    private static class InjectTarget2 {
        @Inject
        @Named("myNameIsB")
        TestComponent tc;
    }

    // --- Constructor injection targets ---

    private static class ConstructorInjectionTarget {
        final TestComponent tc;

        @Inject
        ConstructorInjectionTarget(TestComponent tc) {
            this.tc = tc;
        }
    }

    private static class NamedConstructorInjectionTarget {
        final TestComponent tc;

        @Inject
        NamedConstructorInjectionTarget(@Named("myNameIsB") TestComponent tc) {
            this.tc = tc;
        }
    }

    @SuppressWarnings("unused") // instantiated via reflection in ComponentInjector.newInstance()
    private static class NoInjectConstructorTarget {
        @Inject
        TestComponent tc;

        public NoInjectConstructorTarget() {}
    }

    @SuppressWarnings("unused") // instantiated via reflection in ComponentInjector.newInstance()
    private static class ImplicitConstructorTarget {
        final TestComponent tc;

        ImplicitConstructorTarget(TestComponent tc) {
            this.tc = tc;
        }
    }

    private static class MixedInjectionTarget {
        final TestComponent constructorComponent;
        @Inject
        TestComponent fieldComponent;

        @Inject
        MixedInjectionTarget(TestComponent constructorComponent) {
            this.constructorComponent = constructorComponent;
        }
    }

    private static class MultipleInjectConstructorTarget {
        @SuppressWarnings("unused") // assigned by constructor; class exists only to test error detection
        final TestComponent tc;

        @Inject
        MultipleInjectConstructorTarget(TestComponent tc) {
            this.tc = tc;
        }

        @Inject
        MultipleInjectConstructorTarget(TestComponent tc, String dummy) {
            this.tc = tc;
        }
    }

    @Test
    public void fieldInjectionAcrossClassloaderBoundary() throws Exception {
        // Simulate ConfigurationLoader: a child classloader redefines the
        // injection target (controller/resource) but NOT the component class.
        // The target's @Inject field type (SystemComponent) is loaded by the
        // parent classloader, so Field.set works. However, isAssignableFrom
        // can still fail when the child redefines the target class and it
        // references a type whose hierarchy crosses loaders.
        //
        // More precisely, we test that isCompatibleType matches a component
        // by FQCN when the field's declaring class comes from a different loader.
        ClassLoader parentCl = getClass().getClassLoader();
        // Only redefine CrossLoaderTarget — TestComponent stays in parent
        ClassLoader childCl = new RedefiningClassLoader(parentCl,
                CrossLoaderTarget.class.getName());

        TestComponent component = new TestComponent("cross-cl");
        componentMap.put("comp", component);

        Class<?> targetClass = childCl.loadClass(CrossLoaderTarget.class.getName());
        // The field type SystemComponent is NOT redefined, so it comes from parent.
        // But the declaring class is from the child loader.
        assertThat(targetClass.getClassLoader())
                .as("target class should be loaded by child classloader")
                .isSameAs(childCl);

        ComponentInjector injector = new ComponentInjector(componentMap);
        var ctor = targetClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object target = ctor.newInstance();
        injector.inject(target);

        Field field = targetClass.getDeclaredField("component");
        field.setAccessible(true);
        assertThat(field.get(target))
                .as("component should be injected across classloader boundary")
                .isSameAs(component);
    }

    @Test
    public void isCompatibleTypeMatchesByNameAcrossClassloaders() throws Exception {
        // Directly test that isCompatibleType handles the case where both
        // the field type AND the component class are redefined by child loader,
        // but the component instance was created by the parent loader.
        ClassLoader parentCl = getClass().getClassLoader();
        ClassLoader childCl = new RedefiningClassLoader(parentCl,
                TestComponent.class.getName());

        // child's TestComponent is a different Class object
        Class<?> childTestComponent = childCl.loadClass(TestComponent.class.getName());
        assertThat(childTestComponent).isNotEqualTo(TestComponent.class);
        assertThat(childTestComponent.getName()).isEqualTo(TestComponent.class.getName());

        // isAssignableFrom fails across classloaders
        assertThat(childTestComponent.isAssignableFrom(TestComponent.class)).isFalse();

        // But ComponentInjector's isCompatibleType should match by name
        TestComponent component = new TestComponent("cross-cl");
        componentMap.put("comp", component);
        ComponentInjector injector = new ComponentInjector(componentMap);

        // Use reflection to test the private isCompatibleType method
        var method = ComponentInjector.class.getDeclaredMethod(
                "isCompatibleType", Class.class, Class.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(injector, childTestComponent, component.getClass());
        assertThat(result)
                .as("isCompatibleType should match by FQCN across classloaders")
                .isTrue();
    }

    /**
     * A classloader that redefines specified classes from bytecode,
     * simulating what ConfigurationLoader does during hot-reload.
     */
    private static class RedefiningClassLoader extends ClassLoader {
        private final String[] targetNames;

        RedefiningClassLoader(ClassLoader parent, String... targetNames) {
            super(parent);
            this.targetNames = targetNames;
        }

        private boolean isTarget(String name) {
            for (String t : targetNames) {
                if (name.equals(t)) return true;
            }
            return false;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (isTarget(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c != null) return c;
                    String path = name.replace('.', '/') + ".class";
                    try (InputStream in = getParent().getResourceAsStream(path)) {
                        if (in == null) throw new ClassNotFoundException(name);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) baos.write(buf, 0, len);
                        byte[] bytes = baos.toByteArray();
                        c = defineClass(name, bytes, 0, bytes.length);
                        if (resolve) resolveClass(c);
                        return c;
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                return super.loadClass(name, resolve);
            }
        }
    }

    @SuppressWarnings("unused") // instantiated via reflection in test
    public static class CrossLoaderTarget {
        @Inject
        SystemComponent<?> component;

        public CrossLoaderTarget() {}
    }

    private static class TestComponent extends SystemComponent<TestComponent> {
        private final String id;
        public TestComponent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        protected ComponentLifecycle<TestComponent> lifecycle() {
            return new ComponentLifecycle<>() {
                @Override
                public void start(TestComponent component) {

                }

                @Override
                public void stop(TestComponent component) {

                }
            };
        }
    }
}
