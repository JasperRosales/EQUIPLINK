package src.service;

import src.database.Database;
import src.model.Notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class NotificationService {

    public List<Notification> findForUser(long userId) throws SQLException {
        try { createUpcomingReleaseReminders(userId); } catch (Exception ignored) { }
        String sql = """
                SELECT n.notification_id, n.user_id, n.request_id, n.title,
                       n.message, n.is_read, n.created_at,
                       br.event_date,
                       COALESCE(string_agg(DISTINCT e.equipment_name, ', '), '') AS equipment_names,
                       COALESCE(string_agg(DISTINCT e.category, ', '), '') AS equipment_categories
                FROM notifications n
                LEFT JOIN borrow_requests br ON br.request_id = n.request_id
                LEFT JOIN request_items ri ON ri.request_id = br.request_id
                LEFT JOIN equipment e ON e.equipment_id = COALESCE(
                        ri.assigned_equipment_id, ri.requested_equipment_id)
                WHERE n.user_id = ?
                GROUP BY n.notification_id, n.user_id, n.request_id, n.title,
                         n.message, n.is_read, n.created_at, br.event_date
                ORDER BY n.created_at DESC, n.notification_id DESC
                LIMIT 100
                """;
        List<Notification> notifications = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            Calendar manila = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long requestId = result.getLong("request_id");
                    Long nullableRequestId = result.wasNull() ? null : requestId;
                    java.sql.Date eventDate = result.getDate("event_date");
                    java.sql.Timestamp created = result.getTimestamp("created_at", manila);
                    notifications.add(new Notification(
                            result.getLong("notification_id"),
                            result.getLong("user_id"),
                            nullableRequestId,
                            result.getString("title"),
                            result.getString("message"),
                            result.getBoolean("is_read"),
                            created == null ? null : created.toLocalDateTime(),
                            result.getString("equipment_names"),
                            result.getString("equipment_categories"),
                            eventDate == null ? null : eventDate.toLocalDate()));
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

    public boolean hasPendingReminderToday(long userId) throws SQLException {
        String sql = """
                SELECT 1
                FROM notifications
                WHERE user_id = ?
                  AND title = 'Pending request reminder'
                  AND created_at::date = CURRENT_DATE
                LIMIT 1
                """;
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public void createPendingReminder(long userId, int pendingCount)
            throws SQLException {
        String message = pendingCount == 1
                ? "You have 1 pending request waiting for your action."
                : "You have " + pendingCount
                        + " pending requests waiting for your action.";
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO notifications (user_id, title, message)
                        VALUES (?, 'Pending request reminder', ?)
                        """)) {
            statement.setLong(1, userId);
            statement.setString(2, message);
            statement.executeUpdate();
        }
    }

    /** Notifies faculty borrowers before each upcoming scheduled equipment release (Asia/Manila). */
    public void createUpcomingReleaseReminders(long userId) throws SQLException {
        String sql = """
                SELECT ro.occurrence_id, br.request_id, br.request_code, br.borrower_id,
                       ro.event_date, ro.start_time, ro.venue
                FROM request_occurrences ro
                JOIN borrow_requests br ON br.request_id = ro.request_id
                JOIN users u ON u.user_id = br.borrower_id
                WHERE br.borrower_id = ?
                  AND u.borrower_type = 'FACULTY'
                  AND br.status IN ('APPROVED', 'RELEASED')
                  AND COALESCE(ro.release_notified, FALSE) = FALSE
                  AND (ro.event_date::timestamp + ro.start_time)
                      <= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila') + INTERVAL '1 day')
                  AND (ro.event_date::timestamp + ro.start_time)
                      >= ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila') - INTERVAL '2 hours')
                """;
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long occId = rs.getLong("occurrence_id");
                        long reqId = rs.getLong("request_id");
                        String code = rs.getString("request_code");
                        String msg = "Reminder: equipment release for " + code + " is scheduled on "
                                + rs.getDate("event_date").toLocalDate() + " "
                                + rs.getTime("start_time").toLocalTime()
                                + " at " + rs.getString("venue") + ".";
                        try (PreparedStatement ins = connection.prepareStatement("""
                                INSERT INTO notifications (user_id, request_id, occurrence_id, title, message)
                                VALUES (?, ?, ?, 'Upcoming equipment release', ?)
                                """)) {
                            ins.setLong(1, userId);
                            ins.setLong(2, reqId);
                            ins.setLong(3, occId);
                            ins.setString(4, msg);
                            ins.executeUpdate();
                        }
                        try (PreparedStatement upd = connection.prepareStatement(
                                "UPDATE request_occurrences SET release_notified = TRUE WHERE occurrence_id = ?")) {
                            upd.setLong(1, occId);
                            upd.executeUpdate();
                        }
                    }
                }
            }
            connection.commit();
        } catch (SQLException e) {
            // ignore if schema not migrated yet
            if (e.getMessage() != null && e.getMessage().contains("release_notified")) return;
            throw e;
        }
    }
}
