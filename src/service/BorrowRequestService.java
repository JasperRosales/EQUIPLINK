package src.service;

import src.database.Database;
import src.model.BorrowRequest;
import src.model.BorrowRequestForm;
import src.model.EquipmentRequestItem;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BorrowRequestService {

    private static final String[] EDITABLE_STATUSES = {
            "PENDING_STAFF", "NEEDS_ACTION"
    };

    public List<BorrowRequest> findActiveRequests(long borrowerId) throws SQLException {
        return findRequests(
                "br.borrower_id = ? AND br.status IN "
                        + "('PENDING_STAFF', 'NEEDS_ACTION', 'PENDING_ADMIN', "
                        + "'PENDING_DIRECTOR', 'APPROVED', 'RELEASED')",
                borrowerId);
    }

    public List<BorrowRequest> findBorrowingHistory(long borrowerId) throws SQLException {
        return findRequests(
                "br.borrower_id = ? AND br.status IN ('COMPLETED', 'REJECTED', 'CANCELLED')",
                borrowerId);
    }

    public BorrowRequest findRequest(long borrowerId, long requestId) throws SQLException {
        List<BorrowRequest> requests = findRequests(
                "br.borrower_id = ? AND br.request_id = ?",
                borrowerId, requestId);
        return requests.isEmpty() ? null : requests.get(0);
    }

    public long createRequest(long borrowerId, BorrowRequestForm form) throws Exception {
        validate(form);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<Long, EquipmentRecord> equipment =
                        lockAndValidateEquipment(connection, form, null);
                long requestId = insertRequest(connection, borrowerId, form);
                Map<String, Long> groupIds = createGroups(connection, requestId, equipment);
                insertItems(connection, requestId, form, equipment, groupIds);
                recordHistory(connection, requestId, null, borrowerId,
                        "SUBMITTED", null, "PENDING_STAFF", null);
                connection.commit();
                return requestId;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void updateRequest(
            long borrowerId,
            long requestId,
            BorrowRequestForm form) throws Exception {
        validate(form);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String previousStatus = lockEditableRequest(connection, borrowerId, requestId);
                ensureGroupsArePending(connection, requestId);
                Map<Long, EquipmentRecord> equipment =
                        lockAndValidateEquipment(connection, form, requestId);

                String updateSql = """
                        UPDATE borrow_requests
                        SET venue = ?, event_date = ?, start_time = ?, end_time = ?,
                            purpose = ?, participants = ?, person_in_charge = ?,
                            position = ?, status = 'PENDING_STAFF', remarks = NULL,
                            admin_status = NULL, director_status = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ? AND borrower_id = ?
                        """;
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    bindForm(statement, form, 0);
                    statement.setLong(9, requestId);
                    statement.setLong(10, borrowerId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("The request could not be updated.");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM request_items WHERE request_id = ?")) {
                    statement.setLong(1, requestId);
                    statement.executeUpdate();
                }

                Map<String, Long> groupIds = prepareGroupsForEdit(connection, requestId, equipment);
                insertItems(connection, requestId, form, equipment, groupIds);
                recordHistory(connection, requestId, null, borrowerId,
                        "BORROWER_EDITED", previousStatus, "PENDING_STAFF",
                        "Borrower updated request details.");
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void deleteRequest(long borrowerId, long requestId) throws Exception {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String previousStatus = lockEditableRequest(connection, borrowerId, requestId);
                ensureGroupsArePending(connection, requestId);

                String updateSql = """
                        UPDATE borrow_requests
                        SET status = 'CANCELLED',
                            cancelled_by = ?,
                            cancelled_at = CURRENT_TIMESTAMP,
                            cancellation_reason = 'Cancelled by borrower',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ? AND borrower_id = ?
                        """;
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setLong(1, borrowerId);
                    statement.setLong(2, requestId);
                    statement.setLong(3, borrowerId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("The request could not be cancelled.");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'CANCELLED', cancelled_by = ?,
                            cancelled_at = CURRENT_TIMESTAMP
                        WHERE request_id = ? AND status = 'PENDING'
                        """)) {
                    statement.setLong(1, borrowerId);
                    statement.setLong(2, requestId);
                    statement.executeUpdate();
                }
                recordHistory(connection, requestId, null, borrowerId,
                        "BORROWER_CANCELLED", previousStatus, "CANCELLED",
                        "Cancelled by borrower.");
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public int countActiveForBorrower(long borrowerId) throws SQLException {
        return countRequests(borrowerId,
                "('PENDING_STAFF', 'NEEDS_ACTION', 'PENDING_ADMIN', "
                        + "'PENDING_DIRECTOR', 'APPROVED', 'RELEASED')");
    }

    public int countPendingForBorrower(long borrowerId) throws SQLException {
        return countRequests(borrowerId,
                "('PENDING_STAFF', 'NEEDS_ACTION', 'PENDING_ADMIN', "
                        + "'PENDING_DIRECTOR', 'APPROVED')");
    }

    public int countBorrowedForBorrower(long borrowerId) throws SQLException {
        return countRequests(borrowerId, "('RELEASED')");
    }

    public int countQueueForRole(String role) throws SQLException {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        String sql;
        if ("ICT_STAFF".equals(normalizedRole)
                || "FACILITY_STAFF".equals(normalizedRole)) {
            sql = """
                    SELECT COUNT(DISTINCT br.request_id)
                    FROM borrow_requests br
                    JOIN request_groups rg ON rg.request_id = br.request_id
                    WHERE br.status IN ('PENDING_STAFF', 'NEEDS_ACTION')
                      AND rg.custodian_role = ?
                      AND rg.status = 'PENDING'
                    """;
        } else if ("ADMIN".equals(normalizedRole)) {
            sql = "SELECT COUNT(*) FROM borrow_requests WHERE status = 'PENDING_ADMIN'";
        } else if ("DIRECTOR".equals(normalizedRole)) {
            sql = "SELECT COUNT(*) FROM borrow_requests WHERE status = 'PENDING_DIRECTOR'";
        } else {
            sql = """
                    SELECT COUNT(*) FROM borrow_requests
                    WHERE status IN ('PENDING_STAFF', 'NEEDS_ACTION', 'PENDING_ADMIN', 'PENDING_DIRECTOR')
                    """;
        }

        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if ("ICT_STAFF".equals(normalizedRole)
                    || "FACILITY_STAFF".equals(normalizedRole)) {
                statement.setString(1, normalizedRole);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public int countByStatus(String status) throws SQLException {
        Set<String> allowedStatuses = Set.of(
                "PENDING_STAFF", "NEEDS_ACTION", "PENDING_ADMIN",
                "PENDING_DIRECTOR", "APPROVED", "RELEASED", "COMPLETED",
                "REJECTED", "CANCELLED");
        if (status == null || !allowedStatuses.contains(status)) {
            throw new IllegalArgumentException("Select a valid request status.");
        }
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM borrow_requests WHERE status = ?")) {
            statement.setString(1, status);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public List<BorrowRequest> findStaffQueue(String role) throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        return findRequests("""
                br.status IN ('PENDING_STAFF', 'NEEDS_ACTION')
                AND EXISTS (
                    SELECT 1
                    FROM request_groups rg
                    WHERE rg.request_id = br.request_id
                      AND rg.custodian_role = ?
                      AND rg.status = 'PENDING'
                )
                """, normalizedRole);
    }

    public List<BorrowRequest> findAdminQueue() throws SQLException {
        return findRequests("br.status = 'PENDING_ADMIN'");
    }

    public List<BorrowRequest> findDirectorQueue() throws SQLException {
        return findRequests("br.status = 'PENDING_DIRECTOR'");
    }

    public List<BorrowRequest> findAllRequests() throws SQLException {
        return findRequests("br.request_id > 0");
    }

    public List<BorrowRequest> findTransactionsForRole(String role) throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        return findRequests("""
                EXISTS (
                    SELECT 1
                    FROM request_groups rg
                    WHERE rg.request_id = br.request_id
                      AND rg.custodian_role = ?
                      AND (
                          rg.assigned_at IS NOT NULL
                          OR rg.status IN ('ASSIGNED', 'RELEASED', 'RETURNED')
                      )
                )
                """, normalizedRole);
    }

    public void assignStaffRequest(String role, long actorId, long requestId)
            throws Exception {
        String normalizedRole = requireCustodianRole(role);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LocalDate eventDate;
                java.time.LocalTime startTime;
                java.time.LocalTime endTime;
                String previousStatus;
                long groupId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT br.status, br.event_date, br.start_time, br.end_time,
                               rg.group_id, rg.status AS group_status
                        FROM borrow_requests br
                        JOIN request_groups rg ON rg.request_id = br.request_id
                        WHERE br.request_id = ? AND rg.custodian_role = ?
                        FOR UPDATE OF br, rg
                        """)) {
                    statement.setLong(1, requestId);
                    statement.setString(2, normalizedRole);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Request was not found.");
                        }
                        previousStatus = result.getString("status");
                        if (!"PENDING_STAFF".equals(previousStatus)
                                && !"NEEDS_ACTION".equals(previousStatus)) {
                            throw new IllegalArgumentException(
                                    "This request is no longer waiting for staff review.");
                        }
                        if (!"PENDING".equals(result.getString("group_status"))) {
                            throw new IllegalArgumentException(
                                    "This equipment group was already processed.");
                        }
                        groupId = result.getLong("group_id");
                        eventDate = result.getDate("event_date").toLocalDate();
                        startTime = result.getTime("start_time").toLocalTime();
                        endTime = result.getTime("end_time").toLocalTime();
                    }
                }

                List<long[]> itemQuantities = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT requested_equipment_id, requested_quantity
                        FROM request_items
                        WHERE group_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, groupId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            itemQuantities.add(new long[] {
                                    result.getLong("requested_equipment_id"),
                                    result.getInt("requested_quantity")
                            });
                        }
                    }
                }
                if (itemQuantities.isEmpty()) {
                    throw new IllegalArgumentException(
                            "There are no equipment items to assign in this request.");
                }

                for (long[] item : itemQuantities) {
                    int available = EquipmentService.findAvailableQuantity(
                            connection, item[0], requestId, eventDate, startTime, endTime);
                    if (item[1] > available) {
                        throw new IllegalArgumentException(
                                "The requested quantity is no longer available for the selected schedule.");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_items
                        SET assigned_equipment_id = requested_equipment_id,
                            assigned_quantity = requested_quantity
                        WHERE group_id = ?
                        """)) {
                    statement.setLong(1, groupId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'ASSIGNED', assigned_by = ?,
                            assigned_at = CURRENT_TIMESTAMP, staff_remarks = NULL
                        WHERE group_id = ? AND status = 'PENDING'
                        """)) {
                    statement.setLong(1, actorId);
                    statement.setLong(2, groupId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Equipment assignment did not complete.");
                    }
                }

                boolean readyForAdmin;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT NOT EXISTS (
                            SELECT 1 FROM request_groups
                            WHERE request_id = ?
                              AND status NOT IN ('ASSIGNED', 'CANCELLED')
                        )
                        """)) {
                    statement.setLong(1, requestId);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        readyForAdmin = result.getBoolean(1);
                    }
                }
                String nextStatus = readyForAdmin ? "PENDING_ADMIN" : previousStatus;
                if (readyForAdmin) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE borrow_requests
                            SET status = 'PENDING_ADMIN', updated_at = CURRENT_TIMESTAMP
                            WHERE request_id = ?
                            """)) {
                        statement.setLong(1, requestId);
                        statement.executeUpdate();
                    }
                }
                recordHistory(connection, requestId, groupId, actorId,
                        "EQUIPMENT_ASSIGNED", previousStatus, nextStatus, null);
                if (readyForAdmin) {
                    notifyBorrower(connection, requestId,
                            "Request ready for approval",
                            "Facility staff completed equipment assignment. Your request is waiting for administrative review.");
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void markStaffRequestNeedsAction(
            String role, long actorId, long requestId, String remarks) throws Exception {
        String normalizedRole = requireCustodianRole(role);
        if (remarks == null || remarks.isBlank()) {
            throw new IllegalArgumentException("Enter a reason for the requested changes.");
        }
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long groupId;
                String previousStatus;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT br.status, rg.group_id, rg.status AS group_status
                        FROM borrow_requests br
                        JOIN request_groups rg ON rg.request_id = br.request_id
                        WHERE br.request_id = ? AND rg.custodian_role = ?
                        FOR UPDATE OF br, rg
                        """)) {
                    statement.setLong(1, requestId);
                    statement.setString(2, normalizedRole);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Request was not found.");
                        }
                        previousStatus = result.getString("status");
                        if (!"PENDING_STAFF".equals(previousStatus)
                                && !"NEEDS_ACTION".equals(previousStatus)) {
                            throw new IllegalArgumentException(
                                    "This request is no longer waiting for staff review.");
                        }
                        if (!"PENDING".equals(result.getString("group_status"))) {
                            throw new IllegalArgumentException(
                                    "This equipment group was already processed.");
                        }
                        groupId = result.getLong("group_id");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'UNABLE', staff_remarks = ?
                        WHERE group_id = ? AND status = 'PENDING'
                        """)) {
                    statement.setString(1, remarks.trim());
                    statement.setLong(2, groupId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_items
                        SET assigned_equipment_id = NULL, assigned_quantity = 0
                        WHERE request_id = ?
                        """)) {
                    statement.setLong(1, requestId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'PENDING', assigned_by = NULL, assigned_at = NULL
                        WHERE request_id = ? AND status = 'ASSIGNED'
                        """)) {
                    statement.setLong(1, requestId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE borrow_requests
                        SET status = 'NEEDS_ACTION', remarks = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ?
                        """)) {
                    statement.setString(1, remarks.trim());
                    statement.setLong(2, requestId);
                    statement.executeUpdate();
                }
                recordHistory(connection, requestId, groupId, actorId,
                        "STAFF_REQUESTED_CHANGES", previousStatus, "NEEDS_ACTION",
                        remarks.trim());
                notifyBorrower(connection, requestId, "Request needs your attention",
                        remarks.trim());
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void reviewByAdmin(long actorId, long requestId, boolean approve, String remarks)
            throws Exception {
        reviewRequest(actorId, requestId, "ADMIN", approve, remarks);
    }

    public void reviewByDirector(long actorId, long requestId, boolean approve, String remarks)
            throws Exception {
        reviewRequest(actorId, requestId, "DIRECTOR", approve, remarks);
    }

    public void releaseStaffAssignment(String role, long actorId, long requestId)
            throws Exception {
        updateStaffTransaction(role, actorId, requestId, true);
    }

    public void recordStaffReturn(String role, long actorId, long requestId)
            throws Exception {
        updateStaffTransaction(role, actorId, requestId, false);
    }

    private void updateStaffTransaction(
            String role, long actorId, long requestId, boolean release) throws Exception {
        String normalizedRole = requireCustodianRole(role);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long groupId;
                String requestStatus;
                String groupStatus;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT br.status, rg.group_id, rg.status AS group_status
                        FROM borrow_requests br
                        JOIN request_groups rg ON rg.request_id = br.request_id
                        WHERE br.request_id = ? AND rg.custodian_role = ?
                        FOR UPDATE OF br, rg
                        """)) {
                    statement.setLong(1, requestId);
                    statement.setString(2, normalizedRole);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Request was not found.");
                        }
                        requestStatus = result.getString("status");
                        groupId = result.getLong("group_id");
                        groupStatus = result.getString("group_status");
                    }
                }

                if (release) {
                    if (!"APPROVED".equals(requestStatus) || !"ASSIGNED".equals(groupStatus)) {
                        throw new IllegalArgumentException(
                                "Only approved equipment assignments can be released.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE request_items
                            SET released_quantity = assigned_quantity
                            WHERE group_id = ?
                            """)) {
                        statement.setLong(1, groupId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE request_groups
                            SET status = 'RELEASED', released_by = ?,
                                released_at = CURRENT_TIMESTAMP
                            WHERE group_id = ? AND status = 'ASSIGNED'
                            """)) {
                        statement.setLong(1, actorId);
                        statement.setLong(2, groupId);
                        statement.executeUpdate();
                    }
                } else {
                    if (!"APPROVED".equals(requestStatus)
                            && !"RELEASED".equals(requestStatus)) {
                        throw new IllegalArgumentException(
                                "Only released equipment can be marked as returned.");
                    }
                    if (!"RELEASED".equals(groupStatus)) {
                        throw new IllegalArgumentException(
                                "Only released equipment can be marked as returned.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE request_items
                            SET returned_quantity = released_quantity
                            WHERE group_id = ?
                            """)) {
                        statement.setLong(1, groupId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE request_groups
                            SET status = 'RETURNED', inspected_by = ?,
                                inspected_at = CURRENT_TIMESTAMP
                            WHERE group_id = ? AND status = 'RELEASED'
                            """)) {
                        statement.setLong(1, actorId);
                        statement.setLong(2, groupId);
                        statement.executeUpdate();
                    }
                }

                String nextStatus = requestStatus;
                String remainingStatuses = release
                        ? "('PENDING', 'ASSIGNED')"
                        : "('PENDING', 'ASSIGNED', 'RELEASED')";
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT NOT EXISTS (SELECT 1 FROM request_groups "
                                + "WHERE request_id = ? AND status IN " + remainingStatuses + ")")) {
                    statement.setLong(1, requestId);
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        if (result.getBoolean(1)) {
                            nextStatus = release ? "RELEASED" : "COMPLETED";
                        }
                    }
                }
                if (!requestStatus.equals(nextStatus)) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE borrow_requests
                            SET status = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE request_id = ?
                            """)) {
                        statement.setString(1, nextStatus);
                        statement.setLong(2, requestId);
                        statement.executeUpdate();
                    }
                }
                recordHistory(connection, requestId, groupId, actorId,
                        release ? "EQUIPMENT_RELEASED" : "EQUIPMENT_RETURNED",
                        requestStatus, nextStatus, null);
                notifyBorrower(connection, requestId,
                        release ? "Equipment released" : "Equipment returned",
                        release
                                ? "Your approved equipment is ready for pickup."
                                : "Your returned equipment has been recorded.");
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void reviewRequest(
            long actorId, long requestId, String reviewerRole,
            boolean approve, String remarks) throws Exception {
        String currentStatus = "ADMIN".equals(reviewerRole)
                ? "PENDING_ADMIN" : "PENDING_DIRECTOR";
        String nextStatus = approve
                ? ("ADMIN".equals(reviewerRole) ? "PENDING_DIRECTOR" : "APPROVED")
                : "REJECTED";
        String statusColumn = "ADMIN".equals(reviewerRole) ? "admin_status" : "director_status";
        String event = reviewerRole + (approve ? "_APPROVED" : "_REJECTED");
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status
                        FROM borrow_requests
                        WHERE request_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, requestId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next() || !currentStatus.equals(result.getString("status"))) {
                            throw new IllegalArgumentException(
                                    "This request is not waiting for your review.");
                        }
                    }
                }
                if (!approve && (remarks == null || remarks.isBlank())) {
                    throw new IllegalArgumentException(
                            "Enter a reason before rejecting the request.");
                }
                String updateSql = """
                        UPDATE borrow_requests
                        SET status = ?, %s = ?, remarks = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ?
                        """.formatted(statusColumn);
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setString(1, nextStatus);
                    statement.setString(2, approve ? "APPROVED" : "REJECTED");
                    statement.setString(3, remarks == null || remarks.isBlank()
                            ? null : remarks.trim());
                    statement.setLong(4, requestId);
                    statement.executeUpdate();
                }
                if (!approve) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE request_groups
                            SET status = 'CANCELLED'
                            WHERE request_id = ?
                              AND status IN ('PENDING', 'ASSIGNED')
                            """)) {
                        statement.setLong(1, requestId);
                        statement.executeUpdate();
                    }
                }
                recordHistory(connection, requestId, null, actorId,
                        event, currentStatus, nextStatus,
                        remarks == null || remarks.isBlank() ? null : remarks.trim());
                notifyBorrower(connection, requestId,
                        approve ? "Request approved" : "Request rejected",
                        approve
                                ? ("ADMIN".equals(reviewerRole)
                                        ? "Your request is waiting for final campus director review."
                                        : "Your equipment request has been approved.")
                                : "Your request was rejected. Remarks: " + remarks.trim());
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void notifyBorrower(
            Connection connection, long requestId, String title, String message)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notifications (user_id, request_id, title, message)
                SELECT borrower_id, request_id, ?, ?
                FROM borrow_requests
                WHERE request_id = ?
                """)) {
            statement.setString(1, title);
            statement.setString(2, message);
            statement.setLong(3, requestId);
            statement.executeUpdate();
        }
    }

    private String requireCustodianRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!"ICT_STAFF".equals(normalizedRole)
                && !"FACILITY_STAFF".equals(normalizedRole)) {
            throw new SecurityException(
                    "Only equipment staff can manage this request group.");
        }
        return normalizedRole;
    }

    private int countRequests(long borrowerId, String statuses) throws SQLException {
        String sql = "SELECT COUNT(*) FROM borrow_requests "
                + "WHERE borrower_id = ? AND status IN " + statuses;
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, borrowerId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private List<BorrowRequest> findRequests(String predicate, Object... parameters)
            throws SQLException {
        String sql = """
                SELECT br.request_id, br.request_code, br.borrower_id,
                       u.full_name AS borrower_name, br.venue, br.event_date,
                       br.start_time, br.end_time, br.purpose, br.participants,
                       br.person_in_charge, br.position, br.status, br.remarks,
                       br.submitted_at,
                       e.equipment_id, e.equipment_code, e.equipment_name, e.category,
                       rg.custodian_role, rg.status AS group_status,
                       ri.requested_quantity, ri.assigned_quantity,
                       ri.released_quantity, ri.returned_quantity
                FROM borrow_requests br
                JOIN users u ON u.user_id = br.borrower_id
                LEFT JOIN request_items ri ON ri.request_id = br.request_id
                LEFT JOIN request_groups rg ON rg.group_id = ri.group_id
                LEFT JOIN equipment e ON e.equipment_id = ri.requested_equipment_id
                WHERE %s
                ORDER BY br.submitted_at DESC, br.request_id DESC, e.equipment_name
                """.formatted(predicate);

        Map<Long, BorrowRequestBuilder> builders = new LinkedHashMap<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long id = result.getLong("request_id");
                    BorrowRequestBuilder builder = builders.get(id);
                    if (builder == null) {
                        builder = new BorrowRequestBuilder(result);
                        builders.put(id, builder);
                    }
                    if (result.getObject("equipment_id") != null) {
                        builder.items.add(new EquipmentRequestItem(
                                result.getLong("equipment_id"),
                                result.getString("equipment_code"),
                                result.getString("equipment_name"),
                                result.getString("category"),
                                result.getInt("requested_quantity"),
                                result.getInt("assigned_quantity"),
                                result.getInt("released_quantity"),
                                result.getInt("returned_quantity"),
                                result.getString("custodian_role"),
                                result.getString("group_status")));
                    }
                }
            }
        }

        List<BorrowRequest> requests = new ArrayList<>();
        for (BorrowRequestBuilder builder : builders.values()) {
            requests.add(builder.build());
        }
        return requests;
    }

    private void validate(BorrowRequestForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Request details are required.");
        }
        if (form.getVenue().isBlank()
                || form.getEventDate() == null
                || form.getStartTime() == null
                || form.getEndTime() == null
                || form.getPurpose().isBlank()
                || form.getParticipants().isBlank()
                || form.getPersonInCharge().isBlank()
                || form.getPosition().isBlank()) {
            throw new IllegalArgumentException("Complete all required request details.");
        }
        if (form.getVenue().length() > 150
                || form.getParticipants().length() > 255
                || form.getPersonInCharge().length() > 150
                || form.getPosition().length() > 100) {
            throw new IllegalArgumentException(
                    "Some request details are too long. Shorten the venue, participants, person in charge, or position.");
        }
        if (form.getEventDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("The event date cannot be in the past.");
        }
        if (!form.getEndTime().isAfter(form.getStartTime())) {
            throw new IllegalArgumentException("The end time must be after the start time.");
        }
        if (form.getEquipmentItems().isEmpty()) {
            throw new IllegalArgumentException("Add at least one equipment item.");
        }

        Set<Long> equipmentIds = new HashSet<>();
        for (EquipmentRequestItem item : form.getEquipmentItems()) {
            if (item.getEquipmentId() <= 0 || item.getRequestedQuantity() <= 0) {
                throw new IllegalArgumentException("Select equipment and enter a valid quantity.");
            }
            if (!equipmentIds.add(item.getEquipmentId())) {
                throw new IllegalArgumentException(
                        "Each equipment type can only be added once.");
            }
        }
    }

    private Map<Long, EquipmentRecord> lockAndValidateEquipment(
            Connection connection,
            BorrowRequestForm form,
            Long excludedRequestId) throws SQLException {
        long[] ids = form.getEquipmentItems().stream()
                .mapToLong(EquipmentRequestItem::getEquipmentId)
                .sorted()
                .toArray();
        Map<Long, EquipmentRecord> equipment = new HashMap<>();
        String sql = """
                SELECT equipment_id, equipment_code, equipment_name, category,
                       status, custodian_role, total_quantity
                FROM equipment
                WHERE equipment_id = ? AND active = TRUE
                FOR UPDATE
                """;

        for (long id : ids) {
            String name;
            String status;
            String custodianRole;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException(
                                "An equipment item is no longer available.");
                    }
                    name = result.getString("equipment_name");
                    status = result.getString("status");
                    custodianRole = result.getString("custodian_role");
                }
            }
            int available = EquipmentService.findAvailableQuantity(
                    connection, id, excludedRequestId,
                    form.getEventDate(), form.getStartTime(), form.getEndTime());
            EquipmentRecord record = new EquipmentRecord(
                    name, status, custodianRole, available);
            if (!"AVAILABLE".equals(record.status)) {
                throw new IllegalArgumentException(
                        record.name + " is not currently available.");
            }
            equipment.put(id, record);
        }

        for (EquipmentRequestItem item : form.getEquipmentItems()) {
            EquipmentRecord record = equipment.get(item.getEquipmentId());
            if (record == null || item.getRequestedQuantity() > record.availableQuantity) {
                throw new IllegalArgumentException(
                        "Requested quantity exceeds available stock for "
                                + (record == null ? "equipment" : record.name) + ".");
            }
        }
        return equipment;
    }

    private long insertRequest(
            Connection connection,
            long borrowerId,
            BorrowRequestForm form) throws SQLException {
        String sql = """
                INSERT INTO borrow_requests (
                    request_code, borrower_id, venue, event_date, start_time,
                    end_time, purpose, participants, person_in_charge, position,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_STAFF')
                """;
        String code = "EL-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 10).toUpperCase();
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setLong(2, borrowerId);
            bindForm(statement, form, 2);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return a request ID.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void bindForm(
            PreparedStatement statement,
            BorrowRequestForm form,
            int offset) throws SQLException {
        statement.setString(1 + offset, form.getVenue());
        statement.setDate(2 + offset, Date.valueOf(form.getEventDate()));
        statement.setTime(3 + offset, Time.valueOf(form.getStartTime()));
        statement.setTime(4 + offset, Time.valueOf(form.getEndTime()));
        statement.setString(5 + offset, form.getPurpose());
        statement.setString(6 + offset, form.getParticipants());
        statement.setString(7 + offset, form.getPersonInCharge());
        statement.setString(8 + offset, form.getPosition());
    }

    private Map<String, Long> createGroups(
            Connection connection,
            long requestId,
            Map<Long, EquipmentRecord> equipment) throws SQLException {
        Map<String, Long> groups = new HashMap<>();
        for (EquipmentRecord record : equipment.values()) {
            if (!groups.containsKey(record.custodianRole)) {
                groups.put(record.custodianRole,
                        insertGroup(connection, requestId, record.custodianRole));
            }
        }
        return groups;
    }

    private Map<String, Long> prepareGroupsForEdit(
            Connection connection,
            long requestId,
            Map<Long, EquipmentRecord> equipment) throws SQLException {
        Map<String, Long> groups = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT group_id, custodian_role, status
                FROM request_groups
                WHERE request_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    groups.put(result.getString("custodian_role"),
                            result.getLong("group_id"));
                }
            }
        }

        Set<String> usedRoles = new HashSet<>();
        for (EquipmentRecord record : equipment.values()) {
            if (usedRoles.add(record.custodianRole)
                    && !groups.containsKey(record.custodianRole)) {
                groups.put(record.custodianRole,
                        insertGroup(connection, requestId, record.custodianRole));
            }
        }

        for (Map.Entry<String, Long> entry : groups.entrySet()) {
            if (usedRoles.contains(entry.getKey())) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'PENDING', cancelled_by = NULL, cancelled_at = NULL
                        WHERE group_id = ?
                        """)) {
                    statement.setLong(1, entry.getValue());
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'CANCELLED'
                        WHERE group_id = ? AND status IN ('PENDING', 'UNABLE')
                        """)) {
                    statement.setLong(1, entry.getValue());
                    statement.executeUpdate();
                }
            }
        }
        return groups;
    }

    private long insertGroup(Connection connection, long requestId, String role)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO request_groups (request_id, custodian_role, status)
                VALUES (?, ?, 'PENDING')
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, requestId);
            statement.setString(2, role);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return a request group ID.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertItems(
            Connection connection,
            long requestId,
            BorrowRequestForm form,
            Map<Long, EquipmentRecord> equipment,
            Map<String, Long> groupIds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO request_items (
                    request_id, group_id, requested_equipment_id, requested_quantity
                )
                VALUES (?, ?, ?, ?)
                """)) {
            for (EquipmentRequestItem item : form.getEquipmentItems()) {
                EquipmentRecord record = equipment.get(item.getEquipmentId());
                Long groupId = groupIds.get(record.custodianRole);
                if (groupId == null) {
                    throw new SQLException("Equipment custodian group was not created.");
                }
                statement.setLong(1, requestId);
                statement.setLong(2, groupId);
                statement.setLong(3, item.getEquipmentId());
                statement.setInt(4, item.getRequestedQuantity());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private String lockEditableRequest(
            Connection connection,
            long borrowerId,
            long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT status
                FROM borrow_requests
                WHERE request_id = ? AND borrower_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, requestId);
            statement.setLong(2, borrowerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Request was not found.");
                }
                String status = result.getString("status");
                if (!Arrays.asList(EDITABLE_STATUSES).contains(status)) {
                    throw new IllegalArgumentException(
                            "Only pending requests that have not been processed can be changed.");
                }
                return status;
            }
        }
    }

    private void ensureGroupsArePending(Connection connection, long requestId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM request_groups
                WHERE request_id = ?
                  AND status NOT IN ('PENDING', 'UNABLE', 'CANCELLED')
                """)) {
            statement.setLong(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getInt(1) > 0) {
                    throw new IllegalArgumentException(
                            "This request can no longer be changed because staff has processed it.");
                }
            }
        }
    }

    private void recordHistory(
            Connection connection,
            long requestId,
            Long groupId,
            long actorId,
            String event,
            String fromStatus,
            String toStatus,
            String remarks) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO request_history (
                    request_id, group_id, actor_id, event_type,
                    from_status, to_status, remarks
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, requestId);
            if (groupId == null) {
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(2, groupId);
            }
            statement.setLong(3, actorId);
            statement.setString(4, event);
            statement.setString(5, fromStatus);
            statement.setString(6, toStatus);
            statement.setString(7, remarks);
            statement.executeUpdate();
        }
    }

    private static class EquipmentRecord {
        private final String name;
        private final String status;
        private final String custodianRole;
        private final int availableQuantity;

        private EquipmentRecord(
                String name, String status, String custodianRole, int availableQuantity) {
            this.name = name;
            this.status = status;
            this.custodianRole = custodianRole;
            this.availableQuantity = availableQuantity;
        }
    }

    private static class BorrowRequestBuilder {
        private final long requestId;
        private final String requestCode;
        private final long borrowerId;
        private final String borrowerName;
        private final String venue;
        private final LocalDate eventDate;
        private final java.time.LocalTime startTime;
        private final java.time.LocalTime endTime;
        private final String purpose;
        private final String participants;
        private final String personInCharge;
        private final String position;
        private final String status;
        private final String remarks;
        private final LocalDateTime submittedAt;
        private final List<EquipmentRequestItem> items = new ArrayList<>();

        private BorrowRequestBuilder(ResultSet result) throws SQLException {
            requestId = result.getLong("request_id");
            requestCode = result.getString("request_code");
            borrowerId = result.getLong("borrower_id");
            borrowerName = result.getString("borrower_name");
            venue = result.getString("venue");
            eventDate = result.getDate("event_date").toLocalDate();
            startTime = result.getTime("start_time").toLocalTime();
            endTime = result.getTime("end_time").toLocalTime();
            purpose = result.getString("purpose");
            participants = result.getString("participants");
            personInCharge = result.getString("person_in_charge");
            position = result.getString("position");
            status = result.getString("status");
            remarks = result.getString("remarks");
            submittedAt = result.getTimestamp("submitted_at").toLocalDateTime();
        }

        private BorrowRequest build() {
            return new BorrowRequest(
                    requestId, requestCode, borrowerId, borrowerName, venue,
                    eventDate, startTime, endTime, purpose, participants,
                    personInCharge, position, status, remarks, submittedAt, items);
        }
    }

}
