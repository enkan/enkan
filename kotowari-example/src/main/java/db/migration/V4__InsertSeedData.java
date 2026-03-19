package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Insert seed data for the guestbook example.
 */
public class V4__InsertSeedData extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute("INSERT INTO customer (name, password, email, gender, birthday) VALUES "
                    + "('Alice', 'password', 'alice@example.com', 'F', '1990-01-15')");
            stmt.execute("INSERT INTO customer (name, password, email, gender, birthday) VALUES "
                    + "('Bob', 'password', 'bob@example.com', 'M', '1985-06-20')");
        }
    }
}
