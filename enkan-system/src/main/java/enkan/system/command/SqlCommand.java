package enkan.system.command;

import enkan.component.DataSourceComponent;
import enkan.system.EnkanSystem;
import enkan.system.ReplResponse;
import enkan.system.SystemCommand;
import enkan.system.Transport;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class SqlCommand implements SystemCommand {
    private static final long serialVersionUID = 1L;

    private static final Set<String> WRITE_KEYWORDS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    static {
        WRITE_KEYWORDS.addAll(Arrays.asList(
                "INSERT", "UPDATE", "DELETE", "MERGE",
                "CREATE", "DROP", "TRUNCATE", "ALTER"));
    }

    @Override
    public String shortDescription() {
        return "Execute SQL statements";
    }

    @Override
    public String detailedDescription() {
        return "Execute SQL against the datasource.\nDML statements (INSERT, UPDATE, DELETE, etc.) are auto-committed.\nSELECT results are displayed in table format.\nUsage: /sql <statement>";
    }

    @Override
    public boolean execute(EnkanSystem system, Transport transport, String... args) {
        String sql = String.join(" ", args).trim();
        if (sql.isEmpty()) {
            transport.sendErr("/sql [SQL statement]");
            return true;
        }

        String firstToken = sql.split("\\s+", 2)[0];
        boolean isDML = WRITE_KEYWORDS.contains(firstToken);
        @SuppressWarnings("unchecked")
        List<DataSourceComponent<?>> components = (List<DataSourceComponent<?>>)(List<?>)system.getComponents(DataSourceComponent.class);
        if (components.isEmpty()) {
            transport.sendErr("Not found DataSource");
            return true;
        }
        DataSourceComponent<?> dataSourceComponent = components.getFirst();
        DataSource ds = dataSourceComponent.getDataSource();
        if (ds == null) {
            transport.sendErr("DataSourceComponent is not started");
            return true;
        }

        try(Connection connection = ds.getConnection();
            Statement stmt = connection.createStatement()) {
            connection.setAutoCommit(false);
            if (isDML) {
                int cnt = stmt.executeUpdate(sql);
                connection.commit();
                transport.sendOut(cnt + " updated.");
            } else {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData rsMeta = rs.getMetaData();
                    int cols = rsMeta.getColumnCount();

                    List<ColumnMeta> colMetas = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) {
                        int size = rsMeta.getPrecision(i);
                        if (size == 0) size = 10;
                        if (size >= 20) size = 20;
                        colMetas.add(new ColumnMeta(rsMeta.getColumnName(i), size));
                    }

                    String header = colMetas.stream().map(meta ->
                            String.format(Locale.US, "%-" + meta.dispSize() + "s", meta.name)
                    ).collect(Collectors.joining("|"));
                    transport.send(ReplResponse.withOut(header));
                    transport.send(ReplResponse.withOut(new String(new char[header.length()]).replace("\0", "-")));

                    int cnt = 0;
                    while (rs.next()) {
                        List<String> values = new ArrayList<>();
                        for (int i = 0; i < cols; i++) {
                            values.add(String.format(Locale.US,
                                    "%-" + colMetas.get(i).dispSize() + "s",
                                    rs.getString(i+1)));
                        }
                        transport.send(ReplResponse.withOut(String.join("|", values)));
                        cnt++;
                    }
                    transport.sendOut(cnt + " rows selected");
                }
            }
        } catch (SQLException e) {
            transport.sendErr(e.getLocalizedMessage());
        }
        return true;
    }

    private record ColumnMeta(String name, int dispSize) {
    }
}
