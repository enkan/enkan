package enkan.component.eclipselink;

import enkan.component.ComponentLifecycle;
import enkan.component.DataSourceComponent;
import enkan.component.jpa.EntityManagerProvider;
import enkan.exception.MisconfigurationException;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.logging.slf4j.SLF4JLogger;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.PersistenceUnitTransactionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


/**
 * An {@link enkan.component.jpa.EntityManagerProvider} implementation backed by EclipseLink.
 *
 * <p>On startup, builds an {@link jakarta.persistence.EntityManagerFactory} using
 * the Jakarta Persistence 3.2 {@link PersistenceConfiguration} API.
 * Operates in {@code RESOURCE_LOCAL} transaction mode (no JTA), and bridges EclipseLink
 * logging to SLF4J.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * EclipseLinkEntityManagerProvider provider = BeanBuilder
 *     .builder(new EclipseLinkEntityManagerProvider())
 *     .set(EclipseLinkEntityManagerProvider::setName, "myPU")
 *     .set(EclipseLinkEntityManagerProvider::registerClass, MyEntity.class)
 *     .build();
 * }</pre>
 *
 * @author kawasima
 */
public class EclipseLinkEntityManagerProvider extends EntityManagerProvider<EclipseLinkEntityManagerProvider> {
    /** Entity classes to be managed by JPA. */
    private final List<Class<?>> managedClasses = new ArrayList<>();

    /** SQL log level for EclipseLink. Defaults to {@code "FINE"}. */
    private String sqlLogLevel = "FINE";

    /**
     * Returns the lifecycle of this component.
     *
     * <p>On start, constructs a {@link PersistenceConfiguration} and creates the
     * {@link EntityManagerFactory}. On stop, closes the {@link EntityManagerFactory}.</p>
     *
     * @return the lifecycle of this component
     */
    @Override
    protected ComponentLifecycle<EclipseLinkEntityManagerProvider> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(EclipseLinkEntityManagerProvider component) {
                if (getName() == null || getName().isEmpty()) {
                    throw new MisconfigurationException("core.NULL_OR_EMPTY_ARGUMENT", "name");
                }
                component.setDataSourceComponent(component.getDependency(DataSourceComponent.class));

                PersistenceConfiguration config = new PersistenceConfiguration(getName())
                        .transactionType(PersistenceUnitTransactionType.RESOURCE_LOCAL)
                        .property(PersistenceUnitProperties.NON_JTA_DATASOURCE, getDataSource())
                        .property(PersistenceUnitProperties.CLASSLOADER,
                                Thread.currentThread().getContextClassLoader())
                        .property(PersistenceUnitProperties.SESSION_NAME, UUID.randomUUID().toString())
                        .property(PersistenceUnitProperties.LOGGING_LEVEL + ".sql", sqlLogLevel)
                        .property(PersistenceUnitProperties.LOGGING_LOGGER, SLF4JLogger.class.getName());

                managedClasses.forEach(config::managedClass);

                // Apply any additional JPA properties configured via setJpaProperties()
                getJpaProperties().forEach((k, v) -> config.property((String) k, v));

                component.setEntityManagerFactory(config.createEntityManagerFactory());
            }

            @Override
            public void stop(EclipseLinkEntityManagerProvider component) {
                EntityManagerFactory emf = component.getEntityManagerFactory();
                if (emf != null && emf.isOpen()) {
                    emf.close();
                }
            }
        };
    }

    /**
     * Registers a single entity class to be managed by EclipseLink.
     *
     * @param managedClass the entity class to register
     */
    public void registerClass(Class<?> managedClass) {
        managedClasses.add(managedClass);
    }

    /**
     * Registers multiple entity classes to be managed by EclipseLink.
     *
     * @param classes the entity classes to register
     */
    public void registerClasses(Class<?>... classes) {
        managedClasses.addAll(Arrays.asList(classes));
    }

    /**
     * Sets the log level for SQL statements executed by EclipseLink.
     *
     * <p>Corresponds to the {@code eclipselink.logging.level.sql} property.
     * Accepted values are {@code OFF}, {@code SEVERE}, {@code WARNING}, {@code INFO},
     * {@code CONFIG}, {@code FINE}, {@code FINER}, {@code FINEST}, and {@code ALL}.
     * Defaults to {@code "FINE"}.</p>
     *
     * @param sqlLogLevel the SQL log level string
     */
    public void setSqlLogLevel(String sqlLogLevel) {
        this.sqlLogLevel = sqlLogLevel;
    }
}
