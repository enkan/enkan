package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * @author kawasima
 */
public class V3__CreateGuestbook extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute("CREATE TABLE guestbook " +
                    "(id IDENTITY PRIMARY KEY," +
                    " name VARCHAR(30)," +
                    " message VARCHAR(200)," +
                    " posted_at TIMESTAMP)");
        }
    }
}
