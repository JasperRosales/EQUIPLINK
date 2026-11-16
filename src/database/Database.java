package src.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    /** All timestamps and dates are handled as Philippine time (Asia/Manila, UTC+8). */
    public static final String TIME_ZONE = "Asia/Manila";

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/equiplink";

    private static final String DB_USER = "postgres";

    private static final String DB_PASSWORD = "srhmrc22";

    private Database() {
        // Prevent object instantiation.
    }

    public static Connection getConnection() throws SQLException {

        Connection connection = DriverManager.getConnection(
                DB_URL,
                DB_USER,
                DB_PASSWORD);

        // CURRENT_TIMESTAMP, CURRENT_DATE, and timestamptz rendering then follow
        // Philippine time no matter how the server itself is configured.
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TIME ZONE '" + TIME_ZONE + "'");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }

        return connection;
    }
}
