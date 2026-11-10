package src.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/equiplink";

    private static final String DB_USER = "postgres";

    private static final String DB_PASSWORD = "srhmrc22";

    private Database() {
        // Prevent object instantiation.
    }

    public static Connection getConnection() throws SQLException {

        return DriverManager.getConnection(
                DB_URL,
                DB_USER,
                DB_PASSWORD);
    }
}
