package enkan.component;

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

/**
 * Manages a data source.
 *
 * @author kawasima
 */
public abstract class DataSourceComponent<T extends DataSourceComponent<T>> extends SystemComponent<T> {
    /**
     * Gets the data source that it holds.
     *
     * @return a DataSource, or {@code null} before the component is started
     */
    public abstract @Nullable DataSource getDataSource();
}
