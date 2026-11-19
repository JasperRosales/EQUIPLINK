package src.service;

import src.database.Database;
import src.model.Notification;
import src.model.RequestStatus;
import src.util.UndoPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class NotificationService {

    public List<Notification> findForUser(long userId) throws SQLException {
        try { createUpcomingReleaseReminders(userId); } catch (Exception ignored) { }
        try { runOverdueSweep(); } catch (Exception ignored) { }
        String sql = """
                SELECT n.notification_id, n.user_id, n.request_id, n.title,
                       n.message, n.is_read, n.created_at, n.department, n.action,
                       br.event_date, br.request_code, br.status AS request_status,
                       COALESCE(string_agg(DISTINCT e.equipment_name, ', '), '') AS equipment_names,
                       COALESCE(string_agg(DISTINCT e.category, ', '), '') AS equipment_categories
                FROM notifications n
                LEFT JOIN borrow_requests br ON br.request_id = n.request_id
                LEFT JOIN request_items ri ON ri.request_id = br.request_id
                LEFT JOIN equipment e ON e.equipment_id = COALESCE(
                        ri.assigned_equipment_id, ri.requested_equipment_id)
                WHERE n.user_id = ? AND n.is_deleted = FALSE
                GROUP BY n.notification_id, n.user_id, n.request_id, n.title,
                         n.message, n.is_read, n.created_at, n.department, n.action,
                         br.event_date, br.request_code, br.status
                ORDER BY n.created_at DESC, n.notification_id DESC
                LIMIT 200
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
                            eventDate == null ? null : eventDate.toLocalDate())
                            .withDetails(
                                    result.getString("request_code"),
                                    result.getString("department"),
                                    result.getString("action"),
                                    result.getString("request_status")));
                }
            }
        }
        return notifications;
    }

    public int countUnread(long userId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*) FROM notifications
                        WHERE user_id = ? AND is_read = FALSE AND is_deleted = FALSE
                        """)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public void markRead(long userId, long notificationId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE notifications
                        SET is_read = TRUE, read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                        WHERE notification_id = ? AND user_id = ? AND is_deleted = FALSE
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
                        SET is_read = TRUE, read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                        WHERE user_id = ? AND is_read = FALSE AND is_deleted = FALSE
                        """)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    /**
     * Removes one notification from this user's panel. Only the user's own
     * notification row is touched — requests, approvals, releases, and history stay.
     * The row is soft-deleted so reminders already sent are not generated again.
     */
    public void delete(long userId, long notificationId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE notifications
                        SET read_before_delete = is_read,
                            is_deleted = TRUE,
                            deleted_at = CURRENT_TIMESTAMP,
                            is_read = TRUE
                        WHERE notification_id = ? AND user_id = ? AND is_deleted = FALSE
                        """)) {
            statement.setLong(1, notificationId);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Notification was not found.");
            }
        }
    }

    /** Removes every notification from this user's panel; returns how many. */
    public int deleteAll(long userId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE notifications
                        SET read_before_delete = is_read,
                            is_deleted = TRUE,
                            deleted_at = CURRENT_TIMESTAMP,
                            is_read = TRUE
                        WHERE user_id = ? AND is_deleted = FALSE
                        """)) {
            statement.setLong(1, userId);
            return statement.executeUpdate();
        }
    }

    /**
     * Brings one deleted notification back to the user's list. Restore is only allowed
     * while the shared undo window is open; the original read/unread state is restored.
     */
    public void restore(long userId, long notificationId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE notifications
                        SET is_deleted = FALSE,
                            deleted_at = NULL,
                            is_read = COALESCE(read_before_delete, FALSE),
                            read_before_delete = NULL
                        WHERE notification_id = ? AND user_id = ? AND is_deleted = TRUE
                          AND deleted_at >= CURRENT_TIMESTAMP - """
                        + UndoPolicy.RECOVERY_SQL + """
                         """)) {
            statement.setLong(1, notificationId);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException(
                        "The " + UndoPolicy.RECOVERY_LABEL
                                + " recovery period has ended or the notification no longer exists.");
            }
        }
    }

    /** Restores several notifications deleted together (Undo for "Delete all"). */
    public int restoreAll(long userId, java.util.Collection<Long> notificationIds)
            throws SQLException {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("""
                UPDATE notifications
                SET is_deleted = FALSE,
                    deleted_at = NULL,
                    is_read = COALESCE(read_before_delete, FALSE),
                    read_before_delete = NULL
                WHERE user_id = ? AND is_deleted = TRUE
                  AND deleted_at >= CURRENT_TIMESTAMP - """
                + UndoPolicy.RECOVERY_SQL + " AND notification_id IN (");
        for (int i = 0; i < notificationIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(')');
        int restored = 0;
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setLong(1, userId);
            int index = 2;
            for (Long id : notificationIds) {
                statement.setLong(index++, id == null ? 0L : id);
            }
            restored = statement.executeUpdate();
        }
        if (restored == 0) {
            throw new IllegalArgumentException(
                    "The " + UndoPolicy.RECOVERY_LABEL + " recovery period has ended.");
        }
        return restored;
    }

    /**
     * Cheap fingerprint of request activity plus this user's notifications. Screens
     * poll it and reload only when it changes, keeping every dashboard in sync.
     */
    public String changeToken(long userId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT (SELECT COALESCE(MAX(history_id), 0) FROM request_history),
                               (SELECT COALESCE(MAX(updated_at), TIMESTAMP '1970-01-01')
                                  FROM borrow_requests),
                               (SELECT COUNT(*) FROM borrow_requests),
                               (SELECT COALESCE(MAX(notification_id), 0) FROM notifications
                                 WHERE user_id = ?),
                               (SELECT COUNT(*) FROM notifications
                                 WHERE user_id = ? AND is_deleted = FALSE AND is_read = FALSE),
                               (SELECT COUNT(*) FROM notifications
                                 WHERE user_id = ? AND is_deleted = TRUE)
                        """)) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            statement.setLong(3, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1) + "|" + result.getTimestamp(2) + "|"
                        + result.getLong(3) + "|" + result.getLong(4) + "|"
                        + result.getLong(5) + "|" + result.getLong(6);
            }
        }
    }

    /**
     * Flags work that was not processed before its schedule and notifies the
     * responsible people once. Safe to call from several clients: each overdue row
     * is claimed with an atomic UPDATE ... RETURNING.
     */
    public void runOverdueSweep() throws SQLException {
        Timestamp now = Timestamp.valueOf(
                LocalDateTime.now(ZoneId.of("Asia/Manila")));
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // 1. A department never approved or declined before the schedule started.
                Map<Long, Set<String>> notAssigned = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups rg
                        SET overdue_notified = TRUE
                        WHERE rg.status = 'PENDING' AND rg.overdue_notified = FALSE
                          AND EXISTS (
                              SELECT 1 FROM borrow_requests br
                              WHERE br.request_id = rg.request_id AND br.status = 'PENDING_STAFF'
                          )
                          AND (
                              SELECT COALESCE(ro.event_date, br.event_date)
                                     + COALESCE(ro.start_time, br.start_time)
                              FROM borrow_requests br
                              LEFT JOIN request_occurrences ro
                                     ON ro.occurrence_id = rg.occurrence_id
                              WHERE br.request_id = rg.request_id
                          ) < ?
                        RETURNING rg.request_id, rg.custodian_role
                        """)) {
                    statement.setTimestamp(1, now);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            notAssigned.computeIfAbsent(result.getLong(1), k -> new HashSet<>())
                                    .add(result.getString(2));
                        }
                    }
                }
                for (Map.Entry<Long, Set<String>> entry : notAssigned.entrySet()) {
                    for (String role : entry.getValue()) {
                        String department = RequestStatus.departmentLabel(role);
                        notifyUsers(connection, "role = ?", role, entry.getKey(),
                                "Not assigned on time",
                                "Your department did not approve or decline its equipment before the scheduled date.",
                                role, "OVERDUE");
                        notifyUsers(connection, "role = ?", "ADMIN", entry.getKey(),
                                department + " request not assigned",
                                department + " staff did not process their equipment before the scheduled date.",
                                role, "OVERDUE");
                        notifyBorrower(connection, entry.getKey(),
                                "Request not processed on time",
                                department + " staff did not process your equipment before the scheduled date.",
                                role);
                    }
                }

                // 2. Admin or director review did not happen before the schedule started.
                Map<Long, String> lateReview = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE borrow_requests br
                        SET overdue_notified = TRUE
                        WHERE br.status IN ('PENDING_ADMIN', 'PENDING_DIRECTOR')
                          AND br.overdue_notified = FALSE
                          AND LEAST(br.event_date + br.start_time, COALESCE((
                                  SELECT MIN(ro.event_date + ro.start_time)
                                  FROM request_occurrences ro
                                  WHERE ro.request_id = br.request_id
                              ), br.event_date + br.start_time)) < ?
                        RETURNING br.request_id, br.status
                        """)) {
                    statement.setTimestamp(1, now);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            lateReview.put(result.getLong(1), result.getString(2));
                        }
                    }
                }
                for (Map.Entry<Long, String> entry : lateReview.entrySet()) {
                    String reviewer = "PENDING_ADMIN".equals(entry.getValue()) ? "ADMIN" : "DIRECTOR";
                    notifyUsers(connection, "role = ?", reviewer, entry.getKey(),
                            "Review not processed on time",
                            "This request was not reviewed before its scheduled date.",
                            null, "OVERDUE");
                    notifyBorrower(connection, entry.getKey(),
                            "Request not processed on time",
                            "Your request was not reviewed before its scheduled date.", null);
                }

                // 3. Approved equipment was never released before the schedule ended.
                Map<Long, Set<String>> notReleased = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups rg
                        SET overdue_notified = TRUE
                        WHERE rg.status = 'ASSIGNED' AND rg.overdue_notified = FALSE
                          AND EXISTS (
                              SELECT 1 FROM borrow_requests br
                              WHERE br.request_id = rg.request_id
                                AND br.status IN ('APPROVED', 'RELEASED')
                          )
                          AND (
                              SELECT COALESCE(ro.event_date, br.event_date)
                                     + COALESCE(ro.end_time, br.end_time)
                              FROM borrow_requests br
                              LEFT JOIN request_occurrences ro
                                     ON ro.occurrence_id = rg.occurrence_id
                              WHERE br.request_id = rg.request_id
                          ) < ?
                        RETURNING rg.request_id, rg.custodian_role
                        """)) {
                    statement.setTimestamp(1, now);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            notReleased.computeIfAbsent(result.getLong(1), k -> new HashSet<>())
                                    .add(result.getString(2));
                        }
                    }
                }
                for (Map.Entry<Long, Set<String>> entry : notReleased.entrySet()) {
                    for (String role : entry.getValue()) {
                        String department = RequestStatus.departmentLabel(role);
                        notifyUsers(connection, "role = ?", role, entry.getKey(),
                                "Not released on time",
                                "Approved equipment from your department was not released before the schedule ended.",
                                role, "OVERDUE");
                        notifyUsers(connection, "role = ?", "ADMIN", entry.getKey(),
                                department + " release not processed",
                                department + " staff did not release approved equipment before the schedule ended.",
                                role, "OVERDUE");
                    }
                }

                // 4. Released equipment needs inspection once the usage period ends.
                Map<Long, Set<String>> awaitingInspection = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups rg
                        SET return_notified = TRUE
                        WHERE rg.status = 'RELEASED' AND rg.return_notified = FALSE
                          AND (
                              SELECT COALESCE(ro.event_date, br.event_date)
                                     + COALESCE(ro.end_time, br.end_time)
                              FROM borrow_requests br
                              LEFT JOIN request_occurrences ro
                                     ON ro.occurrence_id = rg.occurrence_id
                              WHERE br.request_id = rg.request_id
                          ) < ?
                        RETURNING rg.request_id, rg.custodian_role
                        """)) {
                    statement.setTimestamp(1, now);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            awaitingInspection.computeIfAbsent(
                                    result.getLong(1), k -> new HashSet<>())
                                    .add(result.getString(2));
                        }
                    }
                }
                for (Map.Entry<Long, Set<String>> entry : awaitingInspection.entrySet()) {
                    for (String role : entry.getValue()) {
                        String department = RequestStatus.departmentLabel(role);
                        notifyUsers(connection, "role = ?", role, entry.getKey(),
                                "Equipment ready for return inspection",
                                "The borrowing period ended. Inspect the returned "
                                        + department + " equipment and record its condition.",
                                role, "RETURN_INSPECTION");
                        notifyUsers(connection, "role = ?", "ADMIN", entry.getKey(),
                                department + " equipment awaiting inspection",
                                "The borrowing period ended and " + department
                                        + " staff need to inspect the returned equipment.",
                                role, "RETURN_INSPECTION");
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void notifyUsers(Connection connection, String predicate, String value,
            long requestId, String title, String message, String department, String action)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO notifications (user_id, request_id, title, message, department, action) "
                        + "SELECT user_id, ?, ?, ?, ?, ? FROM users WHERE active = TRUE "
                        + "AND COALESCE(notifications_enabled, TRUE) = TRUE AND "
                        + predicate)) {
            statement.setLong(1, requestId);
            statement.setString(2, title);
            statement.setString(3, message);
            statement.setString(4, department);
            statement.setString(5, action);
            statement.setString(6, value);
            statement.executeUpdate();
        }
    }

    private void notifyBorrower(Connection connection, long requestId, String title,
            String message, String department) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notifications (user_id, request_id, title, message, department, action)
                SELECT br.borrower_id, br.request_id, ?, ?, ?, 'OVERDUE'
                FROM borrow_requests br
                JOIN users u ON u.user_id = br.borrower_id
                WHERE br.request_id = ?
                  AND COALESCE(u.notifications_enabled, TRUE) = TRUE
                """)) {
            statement.setString(1, title);
            statement.setString(2, message);
            statement.setString(3, department);
            statement.setLong(4, requestId);
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
                        SELECT ?, 'Pending request reminder', ?
                        WHERE EXISTS (
                            SELECT 1 FROM users
                            WHERE user_id = ? AND COALESCE(notifications_enabled, TRUE) = TRUE
                        )
                        """)) {
            statement.setLong(1, userId);
            statement.setString(2, message);
            statement.setLong(3, userId);
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
                  AND COALESCE(u.notifications_enabled, TRUE) = TRUE
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
                                INSERT INTO notifications (user_id, request_id, occurrence_id, title, message, action)
                                VALUES (?, ?, ?, 'Upcoming equipment release', ?, 'REMINDER')
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
