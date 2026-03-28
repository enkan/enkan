package enkan.component.eclipselink;

import enkan.component.ComponentLifecycle;
import enkan.component.DataSourceComponent;
import enkan.component.jpa.EntityManagerProvider;
import enkan.exception.MisconfigurationException;
import enkan.exception.UnreachableException;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.internal.jpa.deployment.SEPersistenceUnitInfo;
import org.eclipse.persistence.logging.slf4j.SLF4JLogger;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceUnitTransactionType;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


/**
 * An {@link enkan.component.jpa.EntityManagerProvider} implementation backed by EclipseLink.
 *
 * <p>On startup, builds an {@link jakarta.persistence.EntityManagerFactory} from
 * {@code persistence.xml} if present on the classpath, or falls back to the classpath root.
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
     * <p>On start, constructs a {@link SEPersistenceUnitInfo} and initializes the
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
                SEPersistenceUnitInfo pu = new SEPersistenceUnitInfo();
                pu.setPersistenceUnitName(getName());
                pu.setClassLoader(Thread.currentThread().getContextClassLoader());
                URL persistenceXmlUrl = getClass().getResource("/META-INF/persistence.xml");
                try {
                    if (persistenceXmlUrl != null) {
                        String s = persistenceXmlUrl.toExternalForm();
                        URI rootUri = URI.create(s.substring(0, s.length() - "persistence.xml".length()));
                        pu.setPersistenceUnitRootUrl(rootUri.toURL());
                    } else {
                        URL rootUrl = Thread.currentThread().getContextClassLoader().getResource("");
                        if (rootUrl != null) {
                            pu.setPersistenceUnitRootUrl(rootUrl);
                        }
                    }
                } catch (MalformedURLException e) {
                    throw new UnreachableException(e);
                }
                pu.setTransactionType(PersistenceUnitTransactionType.RESOURCE_LOCAL);
                pu.setNonJtaDataSource(getDataSource());

                List<String> managedClassNames = managedClasses.stream()
                        .map(Class::getName)
                        .toList();
                pu.setManagedClassNames(managedClassNames);
                pu.setExcludeUnlistedClasses(true);

                getJpaProperties().put(PersistenceUnitProperties.ECLIPSELINK_SE_PUINFO, pu);
                getJpaProperties().put(PersistenceUnitProperties.SESSION_NAME, UUID.randomUUID().toString());
                getJpaProperties().put(PersistenceUnitProperties.LOGGING_LEVEL + ".sql", sqlLogLevel);
                // Bridge EclipseLink logging to SLF4J
                getJpaProperties().put(PersistenceUnitProperties.LOGGING_LOGGER, SLF4JLogger.class.getName());

                component.setEntityManagerFactory(Persistence
                        .createEntityManagerFactory(getName(), getJpaProperties()));
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
