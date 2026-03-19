package db.migration;

import kotowari.example.security.PasswordEncoder;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;

/**
 * Insert seed data for the guestbook example.
 */
public class V4__InsertSeedData extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        String sql = "INSERT INTO customer (name, password, email, gender, birthday) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = context.getConnection().prepareStatement(sql)) {
            insertCustomer(ps, "Alice", "password", "alice@example.com", "F", "1990-01-15");
            insertCustomer(ps, "Bob",   "password", "bob@example.com",   "M", "1985-06-20");
        }
    }

    private void insertCustomer(PreparedStatement ps, String name, String rawPassword,
                                String email, String gender, String birthday) throws Exception {
        ps.setString(1, name);
        ps.setString(2, PasswordEncoder.encode(rawPassword));
        ps.setString(3, email);
        ps.setString(4, gender);
        ps.setString(5, birthday);
        ps.executeUpdate();
    }
}
