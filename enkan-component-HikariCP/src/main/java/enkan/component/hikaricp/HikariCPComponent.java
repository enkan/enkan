package enkan.component.hikaricp;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import enkan.collection.OptionMap;
import enkan.component.ComponentLifecycle;
import enkan.component.DataSourceComponent;
import enkan.component.HealthCheckable;
import enkan.component.HealthStatus;
import enkan.exception.MisconfigurationException;

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Enkan component for HikariCP.
 *
 * @author kawasima
 */
public class HikariCPComponent extends DataSourceComponent<HikariCPComponent> implements HealthCheckable {
    private HikariConfig config;
    private @Nullable HikariDataSource dataSource;

    public HikariCPComponent() {
        config = new HikariConfig();
    }

    /**
     * Creates a HikariCPComponent configured from the given options.
     *
     * <p>Supported option keys:
     * <ul>
     *   <li>{@code "uri"}         — JDBC URL (e.g. {@code "jdbc:h2:mem:test"})</li>
     *   <li>{@code "username"}    — database user name</li>
     *   <li>{@code "password"}    — database password</li>
     *   <li>{@code "autoCommit?"} — whether auto-commit is enabled (boolean)</li>
     *   <li>{@code "connTimeout"} — connection timeout in milliseconds (long)</li>
     *   <li>{@code "idleTimeout"} — idle timeout in milliseconds (long)</li>
     *   <li>{@code "maxLifetime"} — maximum connection lifetime in milliseconds (long)</li>
     *   <li>{@code "maxPoolSize"} — maximum pool size (int)</li>
     *   <li>{@code "minIdle"}     — minimum idle connections (int)</li>
     *   <li>{@code "poolName"}    — pool name for JMX and logging</li>
     *   <li>{@code "schema"}      — default schema</li>
     * </ul>
     *
     * @param options configuration options
     */
    public HikariCPComponent(OptionMap options) {
        this();
        if (options.containsKey("uri")) config.setJdbcUrl(options.getString("uri"));
        if (options.containsKey("username")) config.setUsername(options.getString("username"));
        if (options.containsKey("password")) config.setPassword(options.getString("password"));
        if (options.containsKey("autoCommit?")) config.setAutoCommit(options.getBoolean("autoCommit?"));
        if (options.containsKey("connTimeout")) config.setConnectionTimeout(options.getLong("connTimeout"));
        if (options.containsKey("idleTimeout")) config.setIdleTimeout(options.getLong("idleTimeout"));
        if (options.containsKey("maxLifetime")) config.setMaxLifetime(options.getLong("maxLifetime"));
        if (options.containsKey("maxPoolSize")) config.setMaximumPoolSize(options.getInt("maxPoolSize"));
        if (options.containsKey("minIdle")) config.setMinimumIdle(options.getInt("minIdle"));
        if (options.containsKey("poolName")) config.setPoolName(options.getString("poolName"));
        if (options.containsKey("schema")) config.setSchema(options.getString("schema"));
    }

    @Override
    public @Nullable DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public HealthStatus health() {
        if (dataSource == null || dataSource.isClosed()) return HealthStatus.DOWN;
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(1) ? HealthStatus.UP : HealthStatus.DOWN;
        } catch (SQLException e) {
            return HealthStatus.DOWN;
        }
    }

    @Override
    protected ComponentLifecycle<HikariCPComponent> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(HikariCPComponent component) {
                try {
                    config.validate();
                } catch (IllegalArgumentException | IllegalStateException e) {
                    throw new MisconfigurationException("hikariCP.CONFIGURATION", e.getMessage());
                }
                component.dataSource = new HikariDataSource(config);
            }

            @Override
            public void stop(HikariCPComponent component) {
                if (component.dataSource != null) {
                    component.dataSource.close();
                    component.dataSource = null;
                }
            }
        };
    }

    /**
     * Replaces the HikariCP configuration with the given one.
     *
     * <p><strong>Note:</strong> calling this method overwrites any configuration
     * previously set via the {@link #HikariCPComponent(OptionMap)} constructor.
     *
     * @param config HikariCP configuration
     */
    public void setConfig(HikariConfig config) {
        this.config = config;
    }

    @Override
    public String toString() {
        return "#HikariCPComponent {\n"
                + "  \"jdbcUrl\": \"" + config.getJdbcUrl() + "\",\n"
                + "  \"username\": \"" + config.getUsername() + "\",\n"
                + "  \"dependencies\": " + dependenciesToString()
                + "\n}";
    }
}
