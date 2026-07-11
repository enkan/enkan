package enkan.component.doma2;

import enkan.component.ComponentLifecycle;
import enkan.component.DataSourceComponent;
import enkan.component.SystemComponent;
import enkan.exception.MisconfigurationException;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.ConfigProvider;
import org.seasar.doma.jdbc.Naming;
import org.seasar.doma.jdbc.SqlFileRepository;
import org.seasar.doma.jdbc.dialect.Dialect;
import org.seasar.doma.jdbc.dialect.StandardDialect;
import org.seasar.doma.jdbc.tx.EnkanLocalTransactionDataSource;
import org.seasar.doma.jdbc.tx.LocalTransactionDataSource;

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

import static enkan.util.ReflectionUtils.*;

/**
 * An enkan component that provides Doma2 {@link Config} and cached DAO instances.
 *
 * <p>Requires a {@link DataSourceComponent} dependency. When {@code useLocalTransaction} is
 * {@code true} (default), the DataSource is automatically wrapped in an
 * {@link EnkanLocalTransactionDataSource} to enable local transaction management.</p>
 *
 * <p>DAO instances are resolved by convention: given {@code com.example.FooDao}, the
 * implementation class {@code com.example.impl.FooDaoImpl} is looked up. Instances are
 * cached per DAO interface for the lifetime of the component.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * EnkanSystem system = EnkanSystem.of(
 *     "doma", BeanBuilder.builder(new DomaProvider())
 *         .set(DomaProvider::setDialect, new H2Dialect())
 *         .set(DomaProvider::setNaming, Naming.SNAKE_LOWER_CASE)
 *         .build(),
 *     "datasource", new HikariCPComponent(...)
 * ).relationships(component("doma").using("datasource"));
 * system.start();
 *
 * DomaProvider doma = system.getComponent("doma");
 * MyDao myDao = doma.getDao(MyDao.class);
 * }</pre>
 *
 * @author kawasima
 */
public class DomaProvider extends SystemComponent<DomaProvider> {
    private @Nullable DataSource dataSource;
    private final ConcurrentHashMap<String, Object> daoCache = new ConcurrentHashMap<>();
    private @Nullable Config defaultConfig;
    private @Nullable Dialect dialect;
    private Naming naming = Naming.DEFAULT;
    private boolean useLocalTransaction = true;
    private int maxRows = 0;
    private int fetchSize = 0;
    private int queryTimeout = 0;
    private int batchSize = 0;

    /**
     * Gets the DAO.
     *
     * @param daoInterface a type of DAO
     * @param <T> a type of DAO
     * @return a DAO of the given class.
     */
    @SuppressWarnings("unchecked")
    public <T> T getDao(Class<? extends T> daoInterface) {
        return (T) daoCache.computeIfAbsent(daoInterface.getName(), key ->
                tryReflection(() -> {
                    Class<? extends T> daoClass;
                    try {
                        String implPackageName = daoInterface.getPackageName() + ".impl";
                        daoClass = (Class<? extends T>) Class.forName(implPackageName + "." + daoInterface.getSimpleName() + "Impl", true, daoInterface.getClassLoader());
                    } catch (ClassNotFoundException ex) {
                        throw new MisconfigurationException("doma2.DAO_IMPL_NOT_FOUND", daoInterface.getName(), ex);
                    }
                    try {
                        Constructor<? extends T> daoConstructor = daoClass.getConstructor(DataSource.class);
                        return daoConstructor.newInstance(dataSource);
                    } catch (NoSuchMethodException e) {
                        Constructor<? extends T> daoConstructor = daoClass.getConstructor(Config.class);
                        return daoConstructor.newInstance(defaultConfig);
                    }

            }));
    }

    @Override
    protected ComponentLifecycle<DomaProvider> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(DomaProvider component) {
                DataSourceComponent<?> dataSourceComponent = component.getDependency(DataSourceComponent.class);
                component.dataSource = dataSourceComponent.getDataSource();

                if (useLocalTransaction && !(component.dataSource instanceof LocalTransactionDataSource)) {
                    component.dataSource = new EnkanLocalTransactionDataSource(component.dataSource);
                }
                if (component.dialect == null) component.dialect = new StandardDialect();
                final DataSource cfgDataSource = java.util.Objects.requireNonNull(component.dataSource);
                final Dialect cfgDialect = java.util.Objects.requireNonNull(component.dialect);
                component.defaultConfig = new Config() {
                    @Override
                    public Naming getNaming() {
                        return component.naming;
                    }

                    @Override
                    public DataSource getDataSource() {
                        return cfgDataSource;
                    }

                    @Override
                    public Dialect getDialect() {
                        return cfgDialect;
                    }

                    @Override
                    public int getMaxRows() {
                        return component.maxRows;
                    }

                    @Override
                    public int getFetchSize() {
                        return component.fetchSize;
                    }

                    @Override
                    public int getQueryTimeout() {
                        return component.queryTimeout;
                    }

                    @Override
                    public int getBatchSize() {
                        return component.batchSize;
                    }
                };
            }

            @Override
            public void stop(DomaProvider component) {
                component.dataSource = null;
                daoCache.values().stream()
                        .filter(ConfigProvider.class::isInstance)
                        .map(ConfigProvider.class::cast)
                        .map(ConfigProvider::getConfig)
                        .map(Config::getSqlFileRepository)
                        .forEach(SqlFileRepository::clearCache);
                daoCache.clear();
            }
        };
    }

    public @Nullable Config getDefaultConfig() {
        return defaultConfig;
    }

    public void setDialect(Dialect dialect) {
        this.dialect = dialect;
    }

    public void setNaming(Naming naming) {
        this.naming = naming;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public void setQueryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setUseLocalTransaction(boolean useLocalTransaction) {
        this.useLocalTransaction = useLocalTransaction;
    }

    @Override
    public String toString() {
        return "#DomaProvider {\n"
                + "  \"dependencies\": " + dependenciesToString()
                + "\n}";
    }
}
