package enkan.component.jpa;

import enkan.component.DataSourceComponent;
import enkan.component.SystemComponent;
import enkan.exception.MisconfigurationException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public abstract class EntityManagerProvider<T extends SystemComponent<T>> extends SystemComponent<T> {
    private @Nullable String name;

    private @Nullable DataSourceComponent<?> dataSourceComponent;

    private Map<String, Object> jpaProperties = new HashMap<>();

    private @Nullable EntityManagerFactory entityManagerFactory;

    public EntityManager createEntityManager() {
        if (entityManagerFactory == null) {
            throw new MisconfigurationException("core.COMPONENT_NOT_FOUND", "EntityManagerFactory", getClass().getSimpleName());
        }
        return entityManagerFactory.createEntityManager();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setJpaProperties(Map<String, Object> jpaProperties) {
        this.jpaProperties = jpaProperties;
    }

    protected @Nullable String getName() {
        return this.name;
    }

    protected Map<String, Object> getJpaProperties() {
        return jpaProperties;
    }

    protected void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public @Nullable EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    protected @Nullable DataSource getDataSource() {
        if (dataSourceComponent == null) {
            throw new MisconfigurationException("core.COMPONENT_NOT_FOUND", "DataSourceComponent", getClass().getSimpleName());
        }
        return dataSourceComponent.getDataSource();
    }

    protected void setDataSourceComponent(DataSourceComponent<?> dataSourceComponent) {
        this.dataSourceComponent = dataSourceComponent;
    }

    protected void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        entityManagerFactory = null;
    }
}
