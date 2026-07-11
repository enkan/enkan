package enkan.component;

import enkan.Application;
import enkan.MiddlewareChain;
import enkan.config.ApplicationFactory;
import enkan.config.ConfigurationLoader;
import enkan.system.inject.ComponentInjector;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;

import static enkan.util.ReflectionUtils.*;

/**
 * Provides an application.
 *
 * @author kawasima
 */
public class ApplicationComponent<AREQ, ARES> extends SystemComponent<ApplicationComponent<AREQ, ARES>> {
    /** An application instance*/
    private @Nullable Application<AREQ, ARES> application;

    /** An application loader */
    private @Nullable ConfigurationLoader loader;

    /** A name of the application factory class */
    private final String factoryClassName;

    private @Nullable ClassLoader originalLoader;

    /** A customizer for an application */
    private @Nullable Function<Application<AREQ,ARES>, Application<AREQ,ARES>> applicationCustomizer;

    public ApplicationComponent(String className) {
        this.factoryClassName = className;
    }

    @Override
    protected ComponentLifecycle<ApplicationComponent<AREQ, ARES>> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(ApplicationComponent<AREQ, ARES> component) {
                if (component.application == null) {
                    component.application = tryReflection(() -> {
                        ConfigurationLoader appLoader = new ConfigurationLoader(getClass().getClassLoader());
                        component.loader = appLoader;
                        component.originalLoader = Thread.currentThread().getContextClassLoader();
                        Thread.currentThread().setContextClassLoader(appLoader);
                        @SuppressWarnings("unchecked")
                        Class<? extends ApplicationFactory<AREQ, ARES>> factoryClass =
                                (Class<? extends ApplicationFactory<AREQ, ARES>>) appLoader.loadClass(factoryClassName);
                        ComponentInjector injector = new ComponentInjector(getAllDependencies());
                        ApplicationFactory<AREQ, ARES> factory = factoryClass.getConstructor().newInstance();
                        Application<AREQ, ARES> app = factory.create(injector);
                        app.getMiddlewareStack().stream()
                                .map(MiddlewareChain::getMiddleware)
                                .forEach(injector::inject);

                        if (applicationCustomizer != null) {
                            app = applicationCustomizer.apply(app);
                        }
                        app.validate();
                        return app;
                    });
                }
            }

            @Override
            public void stop(ApplicationComponent<AREQ, ARES> component) {
                component.application = null;
                component.loader = null;
                if (originalLoader != null) {
                    Thread.currentThread().setContextClassLoader(originalLoader);
                }
            }
        };
    }

    public @Nullable Application<AREQ, ARES> getApplication() {
        return application;
    }

    public @Nullable ConfigurationLoader getLoader() {
        return loader;
    }

    public String getFactoryClassName() {
        return factoryClassName;
    }

    public void setApplicationCustomizer(Function<Application<AREQ,ARES>, Application<AREQ,ARES>> applicationCustomizer) {
        this.applicationCustomizer = applicationCustomizer;
    }

    @Override
    public String toString() {
        return "#ApplicationComponent {\n"
                + "  \"application\": \"" + application + "\",\n"
                + "  \"factoryClassName\": \"" + getFactoryClassName() + "\",\n"
                + "  \"dependencies\": " + dependenciesToString()
                + "\n}";

    }

}
