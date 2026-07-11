package enkan.component.flyway;

import enkan.component.ComponentLifecycle;
import enkan.component.DataSourceComponent;
import enkan.component.SystemComponent;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreLocationPrefix;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A migration component using by Flyway.
 *
 * @author kawasima
 */
public class FlywayMigration extends SystemComponent<FlywayMigration> {
    private static final Logger LOG = LoggerFactory.getLogger(FlywayMigration.class);

    private String @Nullable [] locations;
    private boolean cleanBeforeMigration = false;
    /** The table name for Flyway's schema history. Defaults to "schema_version" (Flyway default: "flyway_schema_history"). */
    private String table = "schema_version";
    private @Nullable Flyway flyway;

    private boolean isMigrationAvailable() {
        return Arrays.stream(Objects.requireNonNull(flyway).getConfiguration().getLocations())
                .anyMatch(l -> {
                    if (CoreLocationPrefix.isClassPath(l)) {
                        return Thread.currentThread().getContextClassLoader().getResource(l.getRootPath()) != null;
                    } else if (CoreLocationPrefix.isFileSystem(l)) {
                        return Files.exists(Paths.get(l.getRootPath()));
                    } else {
                        return false;
                    }
                });
    }

    @Override
    protected ComponentLifecycle<FlywayMigration> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(FlywayMigration component) {
                DataSourceComponent<?> dataSourceComponent = getDependency(DataSourceComponent.class);
                DataSource dataSource = Objects.requireNonNull(dataSourceComponent.getDataSource(),
                        "DataSourceComponent has not been started");
                FluentConfiguration configuration = Flyway.configure(Thread.currentThread().getContextClassLoader())
                        .table(table)
                        .baselineOnMigrate(true)
                        .baselineVersion("0")
                        .dataSource(dataSource);

                // Workaround: https://github.com/flyway/flyway/issues/2182
                try (Connection conn = dataSource.getConnection()) {
                    Optional.ofNullable(conn.getSchema())
                            .ifPresent(configuration::schemas);
                } catch (SQLException e) {
                    LOG.warn("Failed to get schema from connection. "
                            + "Flyway may use the wrong schema.", e);
                }

                if (component.locations != null) {
                    configuration.locations(component.locations);
                }

                if (component.cleanBeforeMigration) {
                    LOG.warn("cleanBeforeMigration is enabled. All data in the database will be deleted before migration.");
                    configuration.cleanDisabled(false);
                }
                component.flyway = configuration.load();

                if (isMigrationAvailable()) {
                    if (component.cleanBeforeMigration) {
                        component.flyway.clean();
                    }
                    component.flyway.migrate();
                }
            }

            @Override
            public void stop(FlywayMigration component) {
                component.flyway = null;
            }
        };
    }

    public void setLocations(String[] locations) {
        this.locations = locations;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setCleanBeforeMigration(boolean cleanBeforeMigration) {
        this.cleanBeforeMigration = cleanBeforeMigration;
    }
}
