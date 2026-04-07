package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * @author kawasima
 */
public class V1__CreateCustomer extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute("CREATE TABLE customer("
                    + "id identity primary key, "
                    + "name varchar(255), "
                    + "password varchar(255), "
                    + "email varchar(255), "
                    + "gender varchar(1), "
                    + "birthday DATE"
                    + ")");
        }
    }
}
