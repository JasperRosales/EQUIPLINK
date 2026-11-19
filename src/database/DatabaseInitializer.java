package src.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class DatabaseInitializer extends DatabaseInitializerPart1 {

    DatabaseInitializer() {
        // Prevent object instantiation.
    }

    public static void initializeDatabase() throws SQLException {

        try (Connection connection = Database.getConnection()) {

            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {

                for (String sql : SQL_STATEMENTS) {
                    statement.execute(sql);
                }

                connection.commit();

                System.out.println(
                        "EquipLink database initialized successfully.");

            } catch (SQLException exception) {

                connection.rollback();

                System.err.println(
                        "Database initialization failed: "
                                + exception.getMessage());

                throw exception;
            }
        }
    }
}
