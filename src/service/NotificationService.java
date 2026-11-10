package src.service;

import src.database.Database;
import src.model.Notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NotificationService {

    public List<Notification> findForUser(long userId) throws SQLException {
        String sql = """
                SELECT notification_id, user_id, request_id, title,
                       message, is_read, created_at
                FROM notifications
                WHERE user_id = ?
                ORDER BY created_at DESC, notification_id DESC
                LIMIT 100
                """;
        List<Notification> notifications = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long requestId = result.getLong("request_id");
                    Long nullableRequestId = result.wasNull() ? null : requestId;
                    notifications.add(new Notification(
                            result.getLong("notification_id"),
                            result.getLong("user_id"),
                            nullableRequestId,
                            result.getString("title"),
                            result.getString("message"),
                            result.getBoolean("is_read"),
                            result.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return notifications;
    }

    public void markRead(long userId, long notificationId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE notifications
                        SET is_read = TRUE
                        WHERE notification_id = ? AND user_id = ?
                        """)) {
            statement.setLong(1, notificationId);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Notification was not found.");
            }
        }
    }

    public void markAllRead(long userId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE notifications
                        SET is_read = TRUE
                        WHERE user_id = ? AND is_read = FALSE
                        """)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }
}
