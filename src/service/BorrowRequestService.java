package src.service;

import src.database.Database;
import src.model.BorrowRequest;
import src.model.BorrowRequestForm;
import src.model.DepartmentStatus;
import src.model.EquipmentRequestItem;
import src.model.ItemInspection;
import src.model.RequestOccurrence;
import src.model.RequestStatus;
import src.util.UndoPolicy;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

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
                List<Long> occurrenceIds = insertOccurrences(connection, requestId, form);
                for (Long occId : occurrenceIds) {
                    Map<String, Long> groupIds = createGroups(connection, requestId, occId, equipment);
                    insertItems(connection, requestId, occId, form, equipment, groupIds);
                }
                recordHistory(connection, requestId, null, borrowerId,
                        "SUBMITTED", null, "PENDING_STAFF", null);
                boolean priority = isFacultyBorrower(connection, borrowerId);
                notifyGroupStaff(connection, requestId, "PENDING",
                        priority ? "Priority: new faculty request" : "New equipment request",
                        priority
                                ? "A faculty borrowing request is waiting for equipment processing. Faculty requests are handled first."
                                : "A new borrowing request is waiting for equipment processing.",
                        "SUBMITTED");
                notifyRole(connection, "ADMIN", requestId,
                        priority ? "New faculty request submitted" : "New request submitted",
                        "A borrower submitted a new equipment request. ICT and Facility staff will review their assigned equipment.",
                        null, "SUBMITTED");
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

                try (PreparedStatement s = connection.prepareStatement(
                        "DELETE FROM request_occurrences WHERE request_id = ?")) {
                    s.setLong(1, requestId);
                    s.executeUpdate();
                }
                try (PreparedStatement s = connection.prepareStatement(
                        "DELETE FROM request_groups WHERE request_id = ?")) {
                    s.setLong(1, requestId);
                    s.executeUpdate();
                }

                List<Long> occurrenceIds = insertOccurrences(connection, requestId, form);
                for (Long occId : occurrenceIds) {
                    Map<String, Long> groupIds = createGroups(connection, requestId, occId, equipment);
                    insertItems(connection, requestId, occId, form, equipment, groupIds);
                }
                recordHistory(connection, requestId, null, borrowerId,
                        "BORROWER_EDITED", previousStatus, "PENDING_STAFF",
                        "Borrower updated request details.");
                notifyGroupStaff(connection, requestId, "PENDING",
                        "Request updated by borrower",
                        "A borrowing request was updated and needs equipment processing again.",
                        "BORROWER_EDITED");
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * Soft-deletes one of the borrower's requests. Every status is deletable, including
     * completed requests. The request's original status is left untouched so Undo can
     * put it back exactly where it was instead of turning it into a cancellation.
     */
    public void deleteRequest(long borrowerId, long requestId) throws Exception {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String previousStatus = lockOwnRequestForDeletion(connection, borrowerId, requestId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE borrow_requests
                        SET deleted_at = CURRENT_TIMESTAMP,
                            deleted_by = ?,
                            deletion_reason = ?,
                            status_before_delete = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ? AND borrower_id = ? AND deleted_at IS NULL
                        """)) {
                    statement.setLong(1, borrowerId);
                    statement.setString(2, "Deleted by borrower");
                    statement.setString(3, previousStatus);
                    statement.setLong(4, requestId);
                    statement.setLong(5, borrowerId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("The request could not be deleted.");
                    }
                }
                recordHistory(connection, requestId, null, borrowerId,
                        "BORROWER_DELETED", previousStatus, previousStatus,
                        "Deleted by borrower; recoverable within "
                                + UndoPolicy.RECOVERY_LABEL + ".");
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /** Restores a request deleted by its borrower, with its original status intact. */
    public void restoreRequest(long borrowerId, long requestId) throws Exception {
        restoreDeletedRequest(borrowerId, borrowerId, requestId, true, "BORROWER_RESTORED");
    }

    /**
     * ICT System Admin deletion is a recoverable soft delete: the row keeps its
     * original status and stays out of every list until it is restored or expires.
     */
    public void deleteRequestAsAdmin(
            String actorRole, long actorId, long requestId, String reason) throws Exception {
        requireIctRole(actorRole);
        String auditReason = reason == null || reason.isBlank()
                ? "Deleted by ICT System Admin" : reason.trim();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String previousStatus = lockRequestStatus(connection, requestId);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE borrow_requests
                        SET deleted_at = CURRENT_TIMESTAMP,
                            deleted_by = ?,
                            deletion_reason = ?,
                            status_before_delete = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ? AND deleted_at IS NULL
                        """)) {
                    statement.setLong(1, actorId);
                    statement.setString(2, auditReason);
                    statement.setString(3, previousStatus);
                    statement.setLong(4, requestId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("The request could not be deleted.");
                    }
                }
                recordHistory(connection, requestId, null, actorId,
                        "ADMIN_DELETED", previousStatus, previousStatus, auditReason);
                notifyBorrower(connection, requestId, "Request deleted",
                        "The ICT System Admin deleted this request. Reason: " + auditReason,
                        null, "ADMIN_DELETED");
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /** Restores a request deleted by the ICT System Admin, with its original status intact. */
    public void restoreRequestAsAdmin(
            String actorRole, long actorId, long requestId) throws Exception {
        requireIctRole(actorRole);
        restoreDeletedRequest(null, actorId, requestId, false, "ADMIN_RESTORED");
    }

    private static void requireIctRole(String actorRole) {
        if (!"ICT_STAFF".equalsIgnoreCase(actorRole == null ? "" : actorRole.trim())) {
            throw new SecurityException("Only the ICT System Admin can delete requests.");
        }
    }

    /**
     * Shared restore path. Only rows deleted inside the recovery window are brought
     * back; the status column is never rewritten, so approvals and completions survive.
     */
    private void restoreDeletedRequest(
            Long borrowerId, long actorId, long requestId,
            boolean mustOwn, String event) throws Exception {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String status;
                String previousStatus;
                Timestamp deletedAt;
                String selectSql = "SELECT status, status_before_delete, deleted_at "
                        + "FROM borrow_requests WHERE request_id = ?"
                        + (mustOwn ? " AND borrower_id = ?" : "") + " FOR UPDATE";
                try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                    statement.setLong(1, requestId);
                    if (mustOwn) {
                        statement.setLong(2, borrowerId == null ? 0L : borrowerId);
                    }
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Request was not found.");
                        }
                        status = result.getString("status");
                        previousStatus = result.getString("status_before_delete");
                        deletedAt = result.getTimestamp("deleted_at");
                    }
                }
                if (deletedAt == null) {
                    throw new IllegalArgumentException("This request is not deleted.");
                }
                LocalDateTime earliest = LocalDateTime.now(MANILA)
                        .minusMinutes(UndoPolicy.RECOVERY_MINUTES);
                if (deletedAt.toLocalDateTime().isBefore(earliest)) {
                    throw new IllegalArgumentException(
                            "The " + UndoPolicy.RECOVERY_LABEL + " recovery period has ended.");
                }

                String updateSql = "UPDATE borrow_requests "
                        + "SET deleted_at = NULL, deleted_by = NULL, deletion_reason = NULL, "
                        + "status_before_delete = NULL, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE request_id = ? AND deleted_at IS NOT NULL"
                        + (mustOwn ? " AND borrower_id = ?" : "");
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setLong(1, requestId);
                    if (mustOwn) {
                        statement.setLong(2, borrowerId == null ? 0L : borrowerId);
                    }
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("The request could not be restored.");
                    }
                }
                recordHistory(connection, requestId, null, actorId, event,
                        previousStatus == null ? status : previousStatus, status,
                        "Restored within the " + UndoPolicy.RECOVERY_LABEL + " recovery window.");
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
                    WHERE br.deleted_at IS NULL
                      AND br.status = 'PENDING_STAFF'
                      AND rg.custodian_role = ?
                      AND rg.status = 'PENDING'
                    """;
        } else if ("ADMIN".equals(normalizedRole)) {
            sql = "SELECT COUNT(*) FROM borrow_requests "
                    + "WHERE deleted_at IS NULL AND status = 'PENDING_ADMIN'";
        } else if ("DIRECTOR".equals(normalizedRole)) {
            sql = "SELECT COUNT(*) FROM borrow_requests "
                    + "WHERE deleted_at IS NULL AND status = 'PENDING_DIRECTOR'";
        } else {
            sql = """
                    SELECT COUNT(*) FROM borrow_requests
                    WHERE deleted_at IS NULL
                      AND status IN ('PENDING_STAFF', 'NEEDS_ACTION',
                                     'PENDING_ADMIN', 'PENDING_DIRECTOR')
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
                        "SELECT COUNT(*) FROM borrow_requests "
                                + "WHERE deleted_at IS NULL AND status = ?")) {
            statement.setString(1, status);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public List<BorrowRequest> findStaffQueue(String role) throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        // NEEDS_ACTION requests wait on the borrower, so they leave every staff queue
        // until the borrower resubmits.
        return prioritized(findRequests("""
                br.status = 'PENDING_STAFF'
                AND EXISTS (
                    SELECT 1
                    FROM request_groups rg
                    WHERE rg.request_id = br.request_id
                      AND rg.custodian_role = ?
                      AND rg.status = 'PENDING'
                )
                """, normalizedRole));
    }

    public List<BorrowRequest> findAdminQueue() throws SQLException {
        return prioritized(findRequests("br.status = 'PENDING_ADMIN'"));
    }

    public List<BorrowRequest> findDirectorQueue() throws SQLException {
        return prioritized(findRequests("br.status = 'PENDING_DIRECTOR'"));
    }

    /** Orders faculty (priority) requests first, keeping the existing order otherwise. */
    private static List<BorrowRequest> prioritized(List<BorrowRequest> requests) {
        requests.sort(BorrowRequest.PRIORITY_FIRST);
        return requests;
    }

    /**
     * Every request visible to the role. Admin and director see all requests; ICT and
     * facility staff only see requests containing equipment from their department.
     */
    public List<BorrowRequest> findAllRequests(String role) throws SQLException {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if ("ICT_STAFF".equals(normalizedRole) || "FACILITY_STAFF".equals(normalizedRole)) {
            return findRequests("""
                    EXISTS (
                        SELECT 1 FROM request_groups rg
                        WHERE rg.request_id = br.request_id AND rg.custodian_role = ?
                    )
                    """, normalizedRole);
        }
        if ("ADMIN".equals(normalizedRole) || "DIRECTOR".equals(normalizedRole)) {
            return findRequests("br.request_id > 0");
        }
        throw new SecurityException("Your account is not allowed to view all requests.");
    }

    public List<BorrowRequest> findAllRequests() throws SQLException {
        return findRequests("br.request_id > 0");
    }

    /** Every request the borrower submitted, in any status. */
    public List<BorrowRequest> findAllForBorrower(long borrowerId) throws SQLException {
        return findRequests("br.borrower_id = ?", borrowerId);
    }

    /**
     * Loads one request if the account may see it: borrowers their own, staff requests
     * with their department's equipment, admin and director any request.
     */
    public BorrowRequest findRequestForUser(String role, long userId, long requestId)
            throws SQLException {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        List<BorrowRequest> requests;
        if ("BORROWER".equals(normalizedRole)) {
            requests = findRequests("br.borrower_id = ? AND br.request_id = ?", userId, requestId);
        } else if ("ICT_STAFF".equals(normalizedRole) || "FACILITY_STAFF".equals(normalizedRole)) {
            requests = findRequests("""
                    br.request_id = ? AND EXISTS (
                        SELECT 1 FROM request_groups rg
                        WHERE rg.request_id = br.request_id AND rg.custodian_role = ?
                    )
                    """, requestId, normalizedRole);
        } else if ("ADMIN".equals(normalizedRole) || "DIRECTOR".equals(normalizedRole)) {
            requests = findRequests("br.request_id = ?", requestId);
        } else {
            requests = List.of();
        }
        return requests.isEmpty() ? null : requests.get(0);
    }

    /**
     * Active hand-overs for the role: department-approved equipment on requests with
     * final approval (ready to release), and released equipment waiting to be returned.
     * Each department is listed independently of the other department's progress.
     */
    public List<BorrowRequest> findScheduleForRole(String role) throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        return prioritized(findRequests("""
                EXISTS (
                    SELECT 1
                    FROM request_groups rg
                    WHERE rg.request_id = br.request_id
                      AND rg.custodian_role = ?
                      AND (
                          (rg.status = 'ASSIGNED' AND br.status IN ('APPROVED', 'RELEASED'))
                          OR rg.status IN ('RELEASED', 'RECEIVED')
                      )
                )
                """, normalizedRole));
    }

    /**
     * Transaction history for the role: every request containing this department's
     * equipment, in any status. Pending, approved, declined, overdue, released,
     * received, and completed records all live here; nothing is archived or deleted.
     */
    public List<BorrowRequest> findHistoryForRole(String role) throws SQLException {
        return findAllRequests(requireCustodianRole(role));
    }
    /**
     * Requests with activity on the given Manila day. Dashboard counters use this so
     * a new day starts with fresh counts; the underlying request and audit records
     * are never removed. History events cover submissions, decisions, reviews,
     * releases, receipt, and returns; overdue notices cover overdue counters.
     */
    public List<BorrowRequest> findDailyActivity(
            String role, long userId, LocalDate day) throws SQLException {
        if (day == null) return List.of();
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        List<BorrowRequest> visible;
        if ("BORROWER".equals(normalizedRole)) {
            visible = findAllForBorrower(userId);
        } else if ("ICT_STAFF".equals(normalizedRole)
                || "FACILITY_STAFF".equals(normalizedRole)
                || "ADMIN".equals(normalizedRole)
                || "DIRECTOR".equals(normalizedRole)) {
            visible = findAllRequests(normalizedRole);
        } else {
            return List.of();
        }
        if (visible.isEmpty()) return visible;

        LocalDate effectiveDay = day == null ? LocalDate.now(MANILA) : day;
        Set<Long> activeIds = new HashSet<>();
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT request_id FROM request_history
                    WHERE request_id IS NOT NULL AND CAST(created_at AS DATE) = ?
                    """)) {
                statement.setDate(1, Date.valueOf(effectiveDay));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        activeIds.add(result.getLong("request_id"));
                    }
                }
            }
            boolean scopedDepartment = "ICT_STAFF".equals(normalizedRole)
                    || "FACILITY_STAFF".equals(normalizedRole);
            String department = scopedDepartment ? normalizedRole : null;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT request_id FROM notifications
                    WHERE request_id IS NOT NULL
                      AND CAST(created_at AS DATE) = ?
                      AND action IN ('OVERDUE', 'RETURN_INSPECTION')
                      AND (? IS NULL OR department = ?)
                    """)) {
                statement.setDate(1, Date.valueOf(effectiveDay));
                statement.setObject(2, department, Types.VARCHAR);
                statement.setObject(3, department, Types.VARCHAR);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        activeIds.add(result.getLong("request_id"));
                    }
                }
            }
        }
        List<BorrowRequest> daily = new ArrayList<>();
        for (BorrowRequest request : visible) {
            if (activeIds.contains(request.getRequestId())) {
                daily.add(request);
            }
        }
        return daily;
    }

    public void assignStaffRequest(String role, long actorId, long requestId)
            throws Exception {
        assignStaffRequest(role, actorId, requestId, null);
    }

    /**
     * Department approval: assigns quantities for the actor's own department only. A
     * null list assigns the full requested quantities; otherwise each entry sets the
     * quantity for one item, where 0 marks the item as unable to fulfill. The other
     * department's records are never read for permission or modified.
     */
    public void assignStaffRequest(String role, long actorId, long requestId,
            List<ItemInspection> assignments) throws Exception {
        String normalizedRole = requireCustodianRole(role);
        String department = RequestStatus.departmentLabel(normalizedRole);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String previousStatus = lockRequestStatus(connection, requestId);
                requireStaffReview(previousStatus);
                List<GroupRow> pending = processablePendingGroups(
                        lockRoleGroups(connection, requestId, normalizedRole));

                Map<Long, ItemInspection> inputs = indexInspections(assignments);
                int totalAssigned = 0;
                int totalItems = 0;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_items
                        SET assigned_equipment_id = requested_equipment_id,
                            assigned_quantity = ?,
                            inspection_result = ?,
                            staff_remarks = ?
                        WHERE group_id = ? AND requested_equipment_id = ?
                        """)) {
                    for (GroupRow group : pending) {
                        Map<Long, Integer> requested = lockGroupItems(connection, group.groupId);
                        for (Map.Entry<Long, Integer> item : requested.entrySet()) {
                            totalItems++;
                            ItemInspection input = inputs.get(item.getKey());
                            int quantity = input == null ? item.getValue() : input.getQuantity();
                            if (quantity < 0 || quantity > item.getValue()) {
                                throw new IllegalArgumentException(
                                        "Assigned quantity must be between 0 and the requested quantity.");
                            }
                            int available = EquipmentService.findAvailableQuantity(
                                    connection, item.getKey(), requestId,
                                    group.eventDate, group.startTime, group.endTime);
                            if (quantity > available) {
                                throw new IllegalArgumentException(
                                        "Only " + available
                                                + " unit(s) are available for the schedule on "
                                                + group.eventDate + ".");
                            }
                            String note = input == null ? null : blankToNull(input.getRemarks());
                            statement.setInt(1, quantity);
                            statement.setString(2, quantity == 0 ? "UNABLE_TO_FULFILL"
                                    : quantity < item.getValue() ? "PARTIAL" : null);
                            statement.setString(3, note);
                            statement.setLong(4, group.groupId);
                            statement.setLong(5, item.getKey());
                            statement.addBatch();
                            totalAssigned += quantity;
                        }
                    }
                    if (totalItems == 0) {
                        throw new IllegalArgumentException(
                                "There are no equipment items to assign in this request.");
                    }
                    if (totalAssigned == 0) {
                        throw new IllegalArgumentException(
                                "No equipment can be assigned. Use \"Decline\" or \"Request changes\" instead.");
                    }
                    statement.executeBatch();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'ASSIGNED', assigned_by = ?,
                            assigned_at = CURRENT_TIMESTAMP, staff_remarks = NULL,
                            declined_by = NULL, declined_at = NULL, overdue_notified = FALSE
                        WHERE group_id IN (%s) AND status = 'PENDING'
                        """.formatted(placeholders(pending.size())))) {
                    statement.setLong(1, actorId);
                    int parameter = 2;
                    for (GroupRow group : pending) {
                        statement.setLong(parameter++, group.groupId);
                    }
                    if (statement.executeUpdate() != pending.size()) {
                        throw new IllegalStateException("Equipment assignment did not complete.");
                    }
                }

                String nextStatus = advanceAfterDepartmentDecision(
                        connection, requestId, previousStatus);
                recordHistory(connection, requestId, pending.get(0).groupId, actorId,
                        "EQUIPMENT_ASSIGNED", previousStatus, nextStatus,
                        department + " approved its equipment.");
                notifyBorrower(connection, requestId,
                        department + " equipment approved",
                        department + " staff approved the " + department
                                + " equipment in your request.",
                        normalizedRole, "DEPARTMENT_APPROVED");
                notifyRole(connection, "ADMIN", requestId,
                        department + " approved equipment",
                        department + " staff approved their assigned equipment for this request.",
                        normalizedRole, "DEPARTMENT_APPROVED");
                notifyDepartment(connection, normalizedRole, requestId, actorId,
                        department + " equipment approved",
                        "Your department approved its equipment for this request.",
                        "DEPARTMENT_APPROVED");
                notifyAfterDepartmentDecision(connection, requestId, previousStatus, nextStatus);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * Department decline: marks only the actor's department equipment as declined.
     * The other department keeps processing; the request is declined overall only
     * when every department declined.
     */
    public void declineStaffRequest(String role, long actorId, long requestId, String remarks)
            throws Exception {
        String normalizedRole = requireCustodianRole(role);
        String department = RequestStatus.departmentLabel(normalizedRole);
        if (remarks == null || remarks.isBlank()) {
            throw new IllegalArgumentException("Enter a reason for declining.");
        }
        String reason = remarks.trim();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String previousStatus = lockRequestStatus(connection, requestId);
                requireStaffReview(previousStatus);
                List<GroupRow> pending = processablePendingGroups(
                        lockRoleGroups(connection, requestId, normalizedRole));
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_items
                        SET assigned_equipment_id = NULL, assigned_quantity = 0,
                            inspection_result = 'DECLINED'
                        WHERE group_id IN (%s)
                        """.formatted(placeholders(pending.size())))) {
                    int parameter = 1;
                    for (GroupRow group : pending) {
                        statement.setLong(parameter++, group.groupId);
                    }
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'DECLINED', declined_by = ?,
                            declined_at = CURRENT_TIMESTAMP, staff_remarks = ?
                        WHERE group_id IN (%s) AND status = 'PENDING'
                        """.formatted(placeholders(pending.size())))) {
                    statement.setLong(1, actorId);
                    statement.setString(2, reason);
                    int parameter = 3;
                    for (GroupRow group : pending) {
                        statement.setLong(parameter++, group.groupId);
                    }
                    statement.executeUpdate();
                }
                String nextStatus = advanceAfterDepartmentDecision(
                        connection, requestId, previousStatus);
                recordHistory(connection, requestId, pending.get(0).groupId, actorId,
                        "DEPARTMENT_DECLINED", previousStatus, nextStatus,
                        department + " declined: " + reason);
                notifyBorrower(connection, requestId,
                        department + " equipment declined",
                        department + " staff declined the " + department
                                + " equipment in your request. Reason: " + reason
                                + ("REJECTED".equals(nextStatus) ? ""
                                        : " Equipment from other departments is still being processed."),
                        normalizedRole, "DEPARTMENT_DECLINED");
                notifyRole(connection, "ADMIN", requestId,
                        department + " declined equipment",
                        department + " staff declined their assigned equipment. Reason: " + reason,
                        normalizedRole, "DEPARTMENT_DECLINED");
                notifyDepartment(connection, normalizedRole, requestId, actorId,
                        department + " equipment declined",
                        "Your department declined its equipment for this request. Reason: " + reason,
                        "DEPARTMENT_DECLINED");
                notifyAfterDepartmentDecision(connection, requestId, previousStatus, nextStatus);
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
                String previousStatus = lockRequestStatus(connection, requestId);
                requireStaffReview(previousStatus);
                List<GroupRow> pending = processablePendingGroups(
                        lockRoleGroups(connection, requestId, normalizedRole));
                // Departments whose approval is reset by the borrower's edit are told so.
                notifyGroupStaff(connection, requestId, "ASSIGNED",
                        "Request sent back to borrower",
                        RequestStatus.departmentLabel(normalizedRole)
                                + " staff requested changes from the borrower. Review the request again after it is resubmitted.",
                        "NEEDS_ACTION");
                String placeholders = String.join(", ",
                        java.util.Collections.nCopies(pending.size(), "?"));
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE request_groups
                        SET status = 'UNABLE', staff_remarks = ?
                        WHERE request_id = ? AND custodian_role = ? AND status = 'PENDING'
                          AND group_id IN (
                        """ + placeholders + ")" )) {
                    int parameter = 1;
                    statement.setString(parameter++, remarks.trim());
                    statement.setLong(parameter++, requestId);
                    statement.setString(parameter++, normalizedRole);
                    for (GroupRow group : pending) {
                        statement.setLong(parameter++, group.groupId);
                    }
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
                recordHistory(connection, requestId, pending.get(0).groupId, actorId,
                        "STAFF_REQUESTED_CHANGES", previousStatus, "NEEDS_ACTION",
                        remarks.trim());
                notifyBorrower(connection, requestId, "Request needs your attention",
                        remarks.trim(), normalizedRole, "NEEDS_ACTION");
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

    public void releaseStaffAssignment(String role, long actorId, long requestId,
            List<ItemInspection> conditions) throws Exception {
        updateStaffTransaction(role, actorId, requestId, true, conditions);
    }

    public void recordStaffReturn(String role, long actorId, long requestId,
            List<ItemInspection> inspections) throws Exception {
        updateStaffTransaction(role, actorId, requestId, false, inspections);
    }

    public void receiveByBorrower(long borrowerId, long requestId) throws Exception {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ApprovalState state = lockApprovalState(connection, requestId, borrowerId);
                if (!RequestStatus.isFinalApprovalGranted(
                        state.status, state.adminStatus, state.directorStatus)) {
                    throw new IllegalArgumentException(
                            "Equipment can only be received after final approval and release.");
                }
                List<GroupRow> groups = lockBorrowerGroups(connection, requestId);
                if (groups.isEmpty()) {
                    throw new IllegalStateException("No equipment groups found for this request.");
                }
                boolean hasReleased = false;
                for (GroupRow group : groups) {
                    if ("RELEASED".equals(group.status)) {
                        hasReleased = true;
                        break;
                    }
                }
                if (!hasReleased) {
                    throw new IllegalArgumentException(
                            "No released equipment is waiting for your confirmation.");
                }
                // Mark every released department group as received, with the borrower
                // and timestamp kept on the group for Transaction History.
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE request_groups SET status='RECEIVED', received_by=?, received_at=CURRENT_TIMESTAMP "
                                + "WHERE request_id=? AND status='RELEASED'")) {
                    ps.setLong(1, borrowerId);
                    ps.setLong(2, requestId);
                    ps.executeUpdate();
                }
                // Do not change the stored request status here; staff still inspects and
                // returns each department's equipment independently.
                recordHistory(connection, requestId, null, borrowerId, "BORROWER_RECEIVED",
                        state.status, state.status, null);
                String message = "Borrower confirmed receiving released equipment for request "
                        + requestId + ".";
                notifyRole(connection, "ICT_STAFF", requestId, "Borrower confirmed receipt",
                        message, null, "BORROWER_RECEIVED");
                notifyRole(connection, "FACILITY_STAFF", requestId, "Borrower confirmed receipt",
                        message, null, "BORROWER_RECEIVED");
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * Releases or inspects the return of the actor's department equipment. The final
     * decision is checked from the stored Facility Admin and Campus Director approvals,
     * so a request can never be released before both required approvals are recorded.
     * Each department is still processed independently.
     */
    private void updateStaffTransaction(
            String role, long actorId, long requestId, boolean release,
            List<ItemInspection> inspections) throws Exception {
        String normalizedRole = requireCustodianRole(role);
        String department = RequestStatus.departmentLabel(normalizedRole);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ApprovalState state = lockApprovalState(connection, requestId, null);
                String requestStatus = state.status;
                List<GroupRow> groups = lockRoleGroups(connection, requestId, normalizedRole);
                if (groups.isEmpty()) {
                    throw new SecurityException(
                            "This request has no equipment assigned to your department.");
                }
                boolean finalApproved = RequestStatus.isFinalApprovalGranted(
                        state.status, state.adminStatus, state.directorStatus);
                GroupRow target;
                String issues = null;
                if (release) {
                    if (!finalApproved) {
                        throw new IllegalArgumentException(stageBlockedReason(requestStatus));
                    }
                    target = firstWithStatus(groups, "ASSIGNED");
                    if (target == null) {
                        throw new IllegalArgumentException(releaseBlockedReason(groups));
                    }
                    recordReleaseConditions(connection, target.groupId, inspections);
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE request_groups
                            SET status = 'RELEASED', released_by = ?,
                                released_at = CURRENT_TIMESTAMP
                            WHERE group_id = ? AND status = 'ASSIGNED'
                            """)) {
                        statement.setLong(1, actorId);
                        statement.setLong(2, target.groupId);
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalStateException("Equipment release did not complete.");
                        }
                    }
                } else {
                    if (!finalApproved) {
                        throw new IllegalArgumentException(stageBlockedReason(requestStatus));
                    }
                    target = firstWithStatus(groups, "RECEIVED");
                    if (target == null) {
                        throw new IllegalArgumentException(
                                "Borrower must confirm receipt (Receive) before you can inspect/record return.");
                    }
                    issues = recordReturnInspection(
                            connection, requestId, target.groupId, inspections);
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE request_groups
                            SET status = 'RETURNED', inspected_by = ?,
                                inspected_at = CURRENT_TIMESTAMP
                            WHERE group_id = ? AND status = 'RECEIVED'
                            """)) {
                        statement.setLong(1, actorId);
                        statement.setLong(2, target.groupId);
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalStateException("Equipment return did not complete.");
                        }
                    }
                }

                String nextStatus = requestStatus;
                String remainingStatuses = release
                        ? "('PENDING', 'ASSIGNED')"
                        : "('PENDING', 'ASSIGNED', 'RELEASED', 'RECEIVED')";
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
                } else {
                    touchRequest(connection, requestId);
                }
                recordHistory(connection, requestId, target.groupId, actorId,
                        release ? "EQUIPMENT_RELEASED" : "EQUIPMENT_RETURNED",
                        requestStatus, nextStatus,
                        issues == null ? department : department + ": " + issues);
                String action = release ? "EQUIPMENT_RELEASED" : "EQUIPMENT_RETURNED";
                if (issues != null) {
                    notifyRole(connection, "ADMIN", requestId,
                            "Damaged or missing equipment",
                            department + " return inspection found issues: " + issues,
                            normalizedRole, action);
                }
                notifyBorrower(connection, requestId,
                        release ? department + " equipment released" : department + " equipment returned",
                        release
                                ? "Your approved " + department + " equipment has been released to you."
                                : issues == null
                                        ? "Your returned " + department + " equipment was inspected with no issues."
                                        : "Your returned " + department + " equipment was inspected. Issues found: " + issues,
                        normalizedRole, action);
                notifyRole(connection, "ADMIN", requestId,
                        release ? department + " released equipment" : department + " received returned equipment",
                        release
                                ? department + " staff released their equipment to the borrower."
                                : department + " staff recorded the equipment return.",
                        normalizedRole, action);
                notifyDepartment(connection, normalizedRole, requestId, actorId,
                        release ? department + " equipment released" : department + " equipment returned",
                        release
                                ? "Your department released its equipment for this request."
                                : "Your department recorded the return of its equipment.",
                        action);
                if ("COMPLETED".equals(nextStatus)) {
                    notifyBorrower(connection, requestId, "Transaction completed",
                            "All equipment has been returned and the request is completed.",
                            null, "COMPLETED");
                    notifyRole(connection, "ADMIN", requestId, "Request completed",
                            "All departments finished processing this request.",
                            null, "COMPLETED");
                }
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
        String reviewer = "ADMIN".equals(reviewerRole) ? "Administrator" : "Campus director";
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String status = lockRequestStatus(connection, requestId);
                if (!currentStatus.equals(status)) {
                    throw new IllegalArgumentException(
                            "This request is not waiting for your review.");
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
                    statement.setString(3, blankToNull(remarks));
                    statement.setLong(4, requestId);
                    statement.executeUpdate();
                }
                if (!approve) {
                    notifyGroupStaff(connection, requestId, "ASSIGNED",
                            "Request declined by " + reviewer.toLowerCase(),
                            reviewer + " declined a request your department approved. Reason: "
                                    + remarks.trim(),
                            event);
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
                        event, currentStatus, nextStatus, blankToNull(remarks));
                if (approve && "ADMIN".equals(reviewerRole)) {
                    notifyRole(connection, "DIRECTOR", requestId,
                            "Request awaiting final approval",
                            "A borrowing request approved by the administrator needs your final decision.",
                            null, event);
                    notifyGroupStaff(connection, requestId, "ASSIGNED",
                            "Request approved by administrator",
                            "A request your department approved passed administrative review and awaits final approval.",
                            event);
                } else if (approve) {
                    notifyGroupStaff(connection, requestId, "ASSIGNED",
                            "Equipment ready for release",
                            "A request was given final approval. Record the equipment condition and release your department's equipment.",
                            event);
                    notifyRole(connection, "ADMIN", requestId,
                            "Request given final approval",
                            "The campus director approved this request. Departments can now release their equipment.",
                            null, event);
                } else if ("DIRECTOR".equals(reviewerRole)) {
                    notifyRole(connection, "ADMIN", requestId,
                            "Request declined by campus director",
                            "The campus director declined this request. Reason: " + remarks.trim(),
                            null, event);
                }
                notifyBorrower(connection, requestId,
                        approve ? "Request approved" : "Request declined",
                        approve
                                ? ("ADMIN".equals(reviewerRole)
                                        ? "Your request is waiting for final campus director review."
                                        : "Your equipment request has been approved. Each department will release its equipment.")
                                : "Your request was declined. Remarks: " + remarks.trim(),
                        null, event);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /** One department group row with its schedule (occurrence or request-level). */
    private static final class GroupRow {
        private final long groupId;
        private final String status;
        private final LocalDate eventDate;
        private final java.time.LocalTime startTime;
        private final java.time.LocalTime endTime;

        private GroupRow(long groupId, String status, LocalDate eventDate,
                java.time.LocalTime startTime, java.time.LocalTime endTime) {
            this.groupId = groupId;
            this.status = status;
            this.eventDate = eventDate;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    /** Request stage plus the two stored review decisions used to gate release. */
    private static final class ApprovalState {
        private final String status;
        private final String adminStatus;
        private final String directorStatus;

        private ApprovalState(String status, String adminStatus, String directorStatus) {
            this.status = status;
            this.adminStatus = adminStatus;
            this.directorStatus = directorStatus;
        }
    }

    /** Locks the request row so concurrent department actions are serialized. */
    private String lockRequestStatus(Connection connection, long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM borrow_requests "
                        + "WHERE request_id = ? AND deleted_at IS NULL FOR UPDATE")) {
            statement.setLong(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Request was not found.");
                }
                return result.getString(1);
            }
        }
    }

    /** Locks a request and returns its stage together with both review decisions. */
    private ApprovalState lockApprovalState(
            Connection connection, long requestId, Long borrowerId) throws SQLException {
        String sql = "SELECT status, admin_status, director_status FROM borrow_requests "
                + "WHERE request_id = ? AND deleted_at IS NULL";
        if (borrowerId != null) {
            sql += " AND borrower_id = ?";
        }
        sql += " FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            if (borrowerId != null) {
                statement.setLong(2, borrowerId);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Request was not found.");
                }
                return new ApprovalState(
                        result.getString("status"),
                        result.getString("admin_status"),
                        result.getString("director_status"));
            }
        }
    }

    private List<GroupRow> lockBorrowerGroups(Connection connection, long requestId) throws SQLException {
        List<GroupRow> groups = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT rg.group_id, rg.status, COALESCE(ro.event_date, br.event_date) AS event_date, "
                        + "COALESCE(ro.start_time, br.start_time) AS start_time, COALESCE(ro.end_time, br.end_time) AS end_time "
                        + "FROM request_groups rg JOIN borrow_requests br ON br.request_id=rg.request_id "
                        + "LEFT JOIN request_occurrences ro ON ro.occurrence_id=rg.occurrence_id "
                        + "WHERE rg.request_id=? ORDER BY 3,4,rg.group_id FOR UPDATE OF rg")) {
            statement.setLong(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    groups.add(new GroupRow(result.getLong("group_id"), result.getString("status"),
                            result.getDate("event_date").toLocalDate(),
                            result.getTime("start_time").toLocalTime(),
                            result.getTime("end_time").toLocalTime()));
                }
            }
        }
        return groups;
    }

    /**
     * Locks a borrower's own request for deletion. Unlike the edit lock, this accepts
     * every status — completed requests are deletable too — but never a deleted row.
     */
    private String lockOwnRequestForDeletion(
            Connection connection, long borrowerId, long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT status
                FROM borrow_requests
                WHERE request_id = ? AND borrower_id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)) {
            statement.setLong(1, requestId);
            statement.setLong(2, borrowerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Request was not found.");
                }
                return result.getString("status");
            }
        }
    }

    private void touchRequest(Connection connection, long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE borrow_requests SET updated_at = CURRENT_TIMESTAMP WHERE request_id = ?")) {
            statement.setLong(1, requestId);
            statement.executeUpdate();
        }
    }

    /** Locks the department's own groups, earliest schedule first. */
    private List<GroupRow> lockRoleGroups(Connection connection, long requestId, String role)
            throws SQLException {
        List<GroupRow> groups = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT rg.group_id, rg.status,
                       COALESCE(ro.event_date, br.event_date) AS event_date,
                       COALESCE(ro.start_time, br.start_time) AS start_time,
                       COALESCE(ro.end_time, br.end_time) AS end_time
                FROM request_groups rg
                JOIN borrow_requests br ON br.request_id = rg.request_id
                LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
                WHERE rg.request_id = ? AND rg.custodian_role = ?
                ORDER BY 3, 4, rg.group_id
                FOR UPDATE OF rg
                """)) {
            statement.setLong(1, requestId);
            statement.setString(2, role);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    groups.add(new GroupRow(
                            result.getLong("group_id"),
                            result.getString("status"),
                            result.getDate("event_date").toLocalDate(),
                            result.getTime("start_time").toLocalTime(),
                            result.getTime("end_time").toLocalTime()));
                }
            }
        }
        return groups;
    }

    private Map<Long, Integer> lockGroupItems(Connection connection, long groupId)
            throws SQLException {
        Map<Long, Integer> requested = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT requested_equipment_id, requested_quantity
                FROM request_items
                WHERE group_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, groupId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    requested.put(result.getLong(1), result.getInt(2));
                }
            }
        }
        return requested;
    }

    private static void requireStaffReview(String requestStatus) {
        if (!"PENDING_STAFF".equals(requestStatus) && !"NEEDS_ACTION".equals(requestStatus)) {
            throw new IllegalArgumentException(
                    "This request is no longer waiting for department review.");
        }
    }

    private static List<GroupRow> processablePendingGroups(List<GroupRow> groups) {
        if (groups.isEmpty()) {
            throw new SecurityException(
                    "This request has no equipment assigned to your department.");
        }
        List<GroupRow> pending = new ArrayList<>();
        for (GroupRow group : groups) {
            if ("PENDING".equals(group.status)) {
                pending.add(group);
            }
        }
        if (pending.isEmpty()) {
            throw new IllegalArgumentException(
                    "Your department already processed this request.");
        }
        return pending;
    }

    /** First group in the given state, ordered by the query's schedule/ID sort. */
    private static GroupRow firstWithStatus(List<GroupRow> groups, String status) {
        for (GroupRow group : groups) {
            if (status.equals(group.status)) {
                return group;
            }
        }
        return null;
    }

    private static String releaseBlockedReason(List<GroupRow> groups) {
        boolean pending = false, declined = false, released = false, cancelled = false;
        for (GroupRow group : groups) {
            switch (group.status) {
                case "PENDING", "UNABLE" -> pending = true;
                case "DECLINED" -> declined = true;
                case "RELEASED", "RETURNED" -> released = true;
                case "CANCELLED", "NOT_REQUIRED" -> cancelled = true;
                default -> { }
            }
        }
        if (pending) return "Your department's equipment is still pending approval.";
        if (cancelled && !released && !declined) {
            return "This request was declined or cancelled, so nothing can be released.";
        }
        if (declined && !released) return "Your department declined this equipment, so it cannot be released.";
        if (released) return "Your department's equipment was already released.";
        return "This equipment cannot be released.";
    }

    private static String stageBlockedReason(String requestStatus) {
        return switch (requestStatus) {
            case "PENDING_STAFF", "NEEDS_ACTION", "PENDING_ADMIN" ->
                    "The request is still waiting for Facility Admin approval.";
            case "PENDING_DIRECTOR" ->
                    "The request is still waiting for Campus Director approval.";
            case "REJECTED" -> "This request was declined and cannot be released.";
            case "CANCELLED" -> "This request was cancelled.";
            default -> "Only approved equipment can be released.";
        };
    }

    /**
     * Moves the request forward once every department decided: to admin review when
     * at least one department approved, or to declined when all departments declined.
     */
    private String advanceAfterDepartmentDecision(
            Connection connection, long requestId, String currentStatus) throws SQLException {
        int undecided;
        int approved;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FILTER (WHERE status IN ('PENDING', 'UNABLE')),
                       COUNT(*) FILTER (WHERE status = 'ASSIGNED')
                FROM request_groups
                WHERE request_id = ?
                """)) {
            statement.setLong(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                undecided = result.getInt(1);
                approved = result.getInt(2);
            }
        }
        if (undecided > 0) {
            touchRequest(connection, requestId);
            return currentStatus;
        }
        String nextStatus = approved > 0 ? "PENDING_ADMIN" : "REJECTED";
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE borrow_requests
                SET status = ?,
                    remarks = CASE WHEN ? THEN 'All departments declined their equipment.'
                                   ELSE remarks END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE request_id = ?
                """)) {
            statement.setString(1, nextStatus);
            statement.setBoolean(2, "REJECTED".equals(nextStatus));
            statement.setLong(3, requestId);
            statement.executeUpdate();
        }
        return nextStatus;
    }

    private void notifyAfterDepartmentDecision(
            Connection connection, long requestId, String previousStatus, String nextStatus)
            throws SQLException {
        if (previousStatus.equals(nextStatus)) {
            return;
        }
        if ("PENDING_ADMIN".equals(nextStatus)) {
            notifyBorrower(connection, requestId,
                    "Request ready for approval",
                    "Every department finished its review. Your request is waiting for administrative review.",
                    null, "PENDING_ADMIN");
            notifyRole(connection, "ADMIN", requestId,
                    "Request awaiting review",
                    "All departments decided on this request. It is waiting for administrative review.",
                    null, "PENDING_ADMIN");
        } else if ("REJECTED".equals(nextStatus)) {
            notifyBorrower(connection, requestId,
                    "Request declined",
                    "Every department declined its equipment, so the request was declined.",
                    null, "DECLINED");
            notifyRole(connection, "ADMIN", requestId,
                    "Request declined by all departments",
                    "Every department declined its equipment for this request.",
                    null, "DECLINED");
        }
    }

    private void recordReleaseConditions(
            Connection connection, long groupId, List<ItemInspection> inspections)
            throws SQLException {
        Map<Long, ItemInspection> inputs = indexInspections(inspections);
        List<long[]> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT ri.request_item_id, ri.requested_equipment_id,
                       ri.assigned_equipment_id, ri.assigned_quantity, e.equipment_name
                FROM request_items ri
                JOIN equipment e ON e.equipment_id = ri.assigned_equipment_id
                WHERE ri.group_id = ? AND ri.assigned_quantity > 0
                ORDER BY ri.assigned_equipment_id
                """)) {
            statement.setLong(1, groupId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    items.add(new long[] { result.getLong(1), result.getLong(2) });
                    long assignedId = result.getLong(3);
                    int quantity = result.getInt(4);
                    String name = result.getString(5);
                    // Final confirmation deducts stock; lock the item so concurrent
                    // releases cannot hand out more units than are on hand.
                    try (PreparedStatement lock = connection.prepareStatement(
                            "SELECT 1 FROM equipment WHERE equipment_id = ? FOR UPDATE")) {
                        lock.setLong(1, assignedId);
                        lock.executeQuery().close();
                    }
                    int onHand = EquipmentService.findAvailableQuantity(
                            connection, assignedId, null, null, null, null);
                    if (quantity > onHand) {
                        throw new IllegalArgumentException("Only " + onHand + " " + name
                                + " on hand; " + quantity + " needed. Wait for returns "
                                + "or adjust the assignment.");
                    }
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE request_items
                SET released_quantity = assigned_quantity,
                    condition_before = ?,
                    staff_remarks = COALESCE(?, staff_remarks)
                WHERE request_item_id = ?
                """)) {
            for (long[] item : items) {
                ItemInspection input = inputs.get(item[1]);
                if (input == null || input.getCondition() == null
                        || input.getCondition().isBlank()) {
                    throw new IllegalArgumentException(
                            "Record the initial condition of every released item.");
                }
                statement.setString(1, limit(input.getCondition(), 80));
                statement.setString(2, blankToNull(input.getRemarks()));
                statement.setLong(3, item[0]);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** Records return inspection; returns an issue summary, or null when none. */
    private String recordReturnInspection(
            Connection connection, long requestId, long groupId,
            List<ItemInspection> inspections) throws SQLException {
        Map<Long, ItemInspection> inputs = indexInspections(inspections);
        StringBuilder issues = new StringBuilder();
        List<Object[]> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT ri.request_item_id, ri.requested_equipment_id,
                       ri.assigned_equipment_id, ri.released_quantity, e.equipment_name
                FROM request_items ri
                JOIN equipment e ON e.equipment_id = ri.requested_equipment_id
                WHERE ri.group_id = ? AND ri.released_quantity > 0
                """)) {
            statement.setLong(1, groupId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    items.add(new Object[] {
                            result.getLong(1), result.getLong(2), result.getLong(3),
                            result.getInt(4), result.getString(5) });
                }
            }
        }
        try (PreparedStatement itemUpdate = connection.prepareStatement("""
                UPDATE request_items
                SET returned_quantity = ?, damaged_quantity = ?, missing_quantity = ?,
                    condition_after = ?, inspection_result = ?,
                    staff_remarks = COALESCE(?, staff_remarks)
                WHERE request_item_id = ?
                """);
                PreparedStatement equipmentUpdate = connection.prepareStatement("""
                        UPDATE equipment
                        SET damaged_quantity = damaged_quantity + ?,
                            lost_quantity = lost_quantity + ?
                        WHERE equipment_id = ?
                        """)) {
            for (Object[] item : items) {
                long requestItemId = (Long) item[0];
                long equipmentId = (Long) item[1];
                long assignedEquipmentId = (Long) item[2];
                int released = (Integer) item[3];
                String name = (String) item[4];
                ItemInspection input = inputs.get(equipmentId);
                if (input == null || input.getCondition() == null
                        || input.getCondition().isBlank()) {
                    throw new IllegalArgumentException(
                            "Record the returned condition of every item.");
                }
                int damaged = input.getDamagedQuantity();
                int missing = input.getMissingQuantity();
                if (damaged < 0 || missing < 0 || damaged + missing > released) {
                    throw new IllegalArgumentException(
                            "Damaged and missing quantities for " + name
                                    + " cannot exceed the " + released + " released.");
                }
                boolean hasIssue = damaged > 0 || missing > 0;
                itemUpdate.setInt(1, released - missing);
                itemUpdate.setInt(2, damaged);
                itemUpdate.setInt(3, missing);
                itemUpdate.setString(4, limit(input.getCondition(), 80));
                itemUpdate.setString(5, hasIssue ? "WITH_ISSUES" : "OK");
                itemUpdate.setString(6, blankToNull(input.getRemarks()));
                itemUpdate.setLong(7, requestItemId);
                itemUpdate.addBatch();
                if (hasIssue) {
                    equipmentUpdate.setInt(1, damaged);
                    equipmentUpdate.setInt(2, missing);
                    equipmentUpdate.setLong(3, assignedEquipmentId);
                    equipmentUpdate.addBatch();
                    if (issues.length() > 0) {
                        issues.append("; ");
                    }
                    issues.append(name).append(" (")
                            .append(damaged).append(" damaged, ")
                            .append(missing).append(" missing)");
                }
            }
            itemUpdate.executeBatch();
            equipmentUpdate.executeBatch();
        }
        if (issues.length() == 0) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE borrow_requests SET has_issues = TRUE WHERE request_id = ?")) {
            statement.setLong(1, requestId);
            statement.executeUpdate();
        }
        return issues.toString();
    }

    private Map<Long, ItemInspection> indexInspections(List<ItemInspection> inspections) {
        Map<Long, ItemInspection> map = new HashMap<>();
        if (inspections != null) {
            for (ItemInspection inspection : inspections) {
                map.put(inspection.getEquipmentId(), inspection);
            }
        }
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limit(String value, int max) {
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private boolean isFacultyBorrower(Connection connection, long borrowerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT borrower_type FROM users WHERE user_id = ?")) {
            statement.setLong(1, borrowerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && "FACULTY".equals(result.getString(1));
            }
        }
    }

    private void notifyRole(
            Connection connection, String role, long requestId, String title, String message)
            throws SQLException {
        notifyRole(connection, role, requestId, title, message, null, null);
    }

    private void notifyRole(
            Connection connection, String role, long requestId, String title, String message,
            String department, String action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notifications (user_id, request_id, title, message, department, action)
                SELECT user_id, ?, ?, ?, ?, ?
                FROM users
                WHERE role = ? AND active = TRUE
                  AND COALESCE(notifications_enabled, TRUE) = TRUE
                """)) {
            statement.setLong(1, requestId);
            statement.setString(2, title);
            statement.setString(3, message);
            statement.setString(4, department);
            statement.setString(5, action);
            statement.setString(6, role);
            statement.executeUpdate();
        }
    }

    /** Notifies every active staff member of one department, except the actor. */
    private void notifyDepartment(
            Connection connection, String role, long requestId, long excludedUserId,
            String title, String message, String action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notifications (user_id, request_id, title, message, department, action)
                SELECT user_id, ?, ?, ?, ?, ?
                FROM users
                WHERE role = ? AND active = TRUE AND user_id <> ?
                  AND COALESCE(notifications_enabled, TRUE) = TRUE
                """)) {
            statement.setLong(1, requestId);
            statement.setString(2, title);
            statement.setString(3, message);
            statement.setString(4, role);
            statement.setString(5, action);
            statement.setString(6, role);
            statement.setLong(7, excludedUserId);
            statement.executeUpdate();
        }
    }

    private void notifyGroupStaff(
            Connection connection, long requestId, String groupStatus,
            String title, String message) throws SQLException {
        notifyGroupStaff(connection, requestId, groupStatus, title, message, null);
    }

    /** Notifies staff whose department group on the request has the given status. */
    private void notifyGroupStaff(
            Connection connection, long requestId, String groupStatus,
            String title, String message, String action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notifications (user_id, request_id, title, message, department, action)
                SELECT DISTINCT u.user_id, rg.request_id, ?, ?, rg.custodian_role, ?
                FROM request_groups rg
                JOIN users u ON u.role = rg.custodian_role AND u.active = TRUE
                WHERE rg.request_id = ? AND rg.status = ?
                  AND COALESCE(u.notifications_enabled, TRUE) = TRUE
                """)) {
            statement.setString(1, title);
            statement.setString(2, message);
            statement.setString(3, action);
            statement.setLong(4, requestId);
            statement.setString(5, groupStatus);
            statement.executeUpdate();
        }
    }

    private void notifyBorrower(
            Connection connection, long requestId, String title, String message)
            throws SQLException {
        notifyBorrower(connection, requestId, title, message, null, null);
    }

    private void notifyBorrower(
            Connection connection, long requestId, String title, String message,
            String department, String action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notifications (user_id, request_id, title, message, department, action)
                SELECT br.borrower_id, br.request_id, ?, ?, ?, ?
                FROM borrow_requests br
                JOIN users u ON u.user_id = br.borrower_id
                WHERE br.request_id = ?
                  AND COALESCE(u.notifications_enabled, TRUE) = TRUE
                """)) {
            statement.setString(1, title);
            statement.setString(2, message);
            statement.setString(3, department);
            statement.setString(4, action);
            statement.setLong(5, requestId);
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
                + "WHERE deleted_at IS NULL AND borrower_id = ? AND status IN " + statuses;
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
                       u.full_name AS borrower_name, u.borrower_type, br.venue, br.event_date,
                       br.start_time, br.end_time, br.purpose, br.participants,
                       br.person_in_charge, br.position, br.status, br.remarks,
                       br.admin_status, br.director_status, br.submitted_at,
                       e.equipment_id, e.equipment_code, e.equipment_name, e.category,
                       rg.custodian_role, rg.status AS group_status,
                       ri.requested_quantity, ri.assigned_quantity,
                       ri.released_quantity, ri.returned_quantity,
                       ri.damaged_quantity, ri.missing_quantity,
                       ri.condition_before, ri.condition_after
                FROM borrow_requests br
                JOIN users u ON u.user_id = br.borrower_id
                LEFT JOIN request_items ri ON ri.request_id = br.request_id
                LEFT JOIN request_groups rg ON rg.group_id = ri.group_id
                LEFT JOIN equipment e ON e.equipment_id = ri.requested_equipment_id
                WHERE br.deleted_at IS NULL AND (%s)
                ORDER BY br.submitted_at DESC, br.request_id DESC, e.equipment_name,
                         ri.request_item_id
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
                        builder.addItem(new EquipmentRequestItem(
                                result.getLong("equipment_id"),
                                result.getString("equipment_code"),
                                result.getString("equipment_name"),
                                result.getString("category"),
                                result.getInt("requested_quantity"),
                                result.getInt("assigned_quantity"),
                                result.getInt("released_quantity"),
                                result.getInt("returned_quantity"),
                                result.getString("custodian_role"),
                                result.getString("group_status"),
                                result.getInt("damaged_quantity"),
                                result.getInt("missing_quantity"),
                                result.getString("condition_before"),
                                result.getString("condition_after")));
                    }
                }
            }
            if (!builders.isEmpty()) {
                loadOccurrences(connection, builders);
                loadDepartments(connection, builders);
            }
        }

        List<BorrowRequest> requests = new ArrayList<>();
        for (BorrowRequestBuilder builder : builders.values()) {
            requests.add(builder.build());
        }
        return requests;
    }

    private static String placeholders(int count) {
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) in.append(',');
            in.append('?');
        }
        return in.toString();
    }

    private void loadOccurrences(Connection connection, Map<Long, BorrowRequestBuilder> builders)
            throws SQLException {
        String q = "SELECT occurrence_id, request_id, event_date, start_time, end_time, venue "
                + "FROM request_occurrences WHERE request_id IN (" + placeholders(builders.size())
                + ") ORDER BY event_date, start_time, occurrence_id";
        try (PreparedStatement ps = connection.prepareStatement(q)) {
            int i = 1;
            for (Long id : builders.keySet()) ps.setLong(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BorrowRequestBuilder b = builders.get(rs.getLong("request_id"));
                    if (b != null) b.occurrences.add(new RequestOccurrence(
                            rs.getLong("occurrence_id"), rs.getLong("request_id"),
                            rs.getDate("event_date").toLocalDate(),
                            rs.getTime("start_time").toLocalTime(),
                            rs.getTime("end_time").toLocalTime(),
                            rs.getString("venue")));
                }
            }
        }
    }

    /** Loads each department's decision, remarks, and release records. */
    private void loadDepartments(Connection connection, Map<Long, BorrowRequestBuilder> builders)
            throws SQLException {
        String q = """
                SELECT rg.group_id, rg.request_id, rg.custodian_role, rg.status,
                       rg.occurrence_id, rg.staff_remarks,
                       COALESCE(ro.event_date, br.event_date) AS event_date,
                       COALESCE(ro.start_time, br.start_time) AS start_time,
                       COALESCE(ro.end_time, br.end_time) AS end_time,
                       CASE WHEN rg.status = 'DECLINED' THEN rg.declined_at
                            ELSE rg.assigned_at END AS decided_at,
                       CASE WHEN rg.status = 'DECLINED' THEN ud.full_name
                            ELSE ua.full_name END AS decided_by,
                       ur.full_name AS released_by, rg.released_at,
                       urcv.full_name AS received_by, rg.received_at,
                       ui.full_name AS inspected_by, rg.inspected_at
                FROM request_groups rg
                JOIN borrow_requests br ON br.request_id = rg.request_id
                LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
                LEFT JOIN users ua ON ua.user_id = rg.assigned_by
                LEFT JOIN users ud ON ud.user_id = rg.declined_by
                LEFT JOIN users ur ON ur.user_id = rg.released_by
                LEFT JOIN users urcv ON urcv.user_id = rg.received_by
                LEFT JOIN users ui ON ui.user_id = rg.inspected_by
                WHERE rg.request_id IN (%s)
                ORDER BY rg.custodian_role, 7, 8, rg.group_id
                """.formatted(placeholders(builders.size()));
        try (PreparedStatement ps = connection.prepareStatement(q)) {
            int i = 1;
            for (Long id : builders.keySet()) ps.setLong(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BorrowRequestBuilder b = builders.get(rs.getLong("request_id"));
                    if (b == null) continue;
                    LocalDate date = rs.getDate("event_date").toLocalDate();
                    long occurrence = rs.getLong("occurrence_id");
                    Long occurrenceId = rs.wasNull() ? null : occurrence;
                    b.departments.add(new DepartmentStatus(
                            rs.getLong("group_id"),
                            rs.getString("custodian_role"),
                            rs.getString("status"),
                            occurrenceId,
                            LocalDateTime.of(date, rs.getTime("start_time").toLocalTime()),
                            LocalDateTime.of(date, rs.getTime("end_time").toLocalTime()),
                            rs.getString("staff_remarks"),
                            rs.getString("decided_by"),
                            toDateTime(rs.getTimestamp("decided_at")),
                            rs.getString("released_by"),
                            toDateTime(rs.getTimestamp("released_at")),
                            rs.getString("received_by"),
                            toDateTime(rs.getTimestamp("received_at")),
                            rs.getString("inspected_by"),
                            toDateTime(rs.getTimestamp("inspected_at"))));
                }
            }
        }
    }

    private static LocalDateTime toDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
    private void validate(BorrowRequestForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Request details are required.");
        }
        List<RequestOccurrence> occs = effectiveOccurrences(form);
        if (occs.isEmpty()) {
            throw new IllegalArgumentException("Add at least one schedule.");
        }
        RequestOccurrence first = occs.get(0);
        if (form.getPurpose().isBlank()
                || form.getParticipants().isBlank()
                || form.getPersonInCharge().isBlank()
                || form.getPosition().isBlank()) {
            throw new IllegalArgumentException("Complete all required request details.");
        }
        if (form.getParticipants().length() > 255
                || form.getPersonInCharge().length() > 150
                || form.getPosition().length() > 100) {
            throw new IllegalArgumentException(
                    "Some request details are too long. Shorten the participants, person in charge, or position.");
        }
        for (RequestOccurrence o : occs) {
            if (o.getVenue() == null || o.getVenue().isBlank() || o.getVenue().length() > 150
                    || o.getEventDate() == null || o.getStartTime() == null || o.getEndTime() == null) {
                throw new IllegalArgumentException("Complete venue, date, start time, and end time for each schedule.");
            }
            if (o.getEventDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Schedule dates cannot be in the past.");
            }
            if (!o.getEndTime().isAfter(o.getStartTime())) {
                throw new IllegalArgumentException("Each schedule's end time must be after its start time.");
            }
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

    private List<RequestOccurrence> effectiveOccurrences(BorrowRequestForm form) {
        if (form.getOccurrences() != null && !form.getOccurrences().isEmpty()) {
            return form.getOccurrences();
        }
        if (form.getEventDate() != null && form.getStartTime() != null && form.getEndTime() != null) {
            return List.of(new RequestOccurrence(form.getEventDate(), form.getStartTime(),
                    form.getEndTime(), form.getVenue()));
        }
        return List.of();
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
            if (!"AVAILABLE".equals(status)) {
                throw new IllegalArgumentException(name + " is not currently available.");
            }
            int minAvailable = Integer.MAX_VALUE;
            for (RequestOccurrence occ : effectiveOccurrences(form)) {
                int available = EquipmentService.findAvailableQuantity(
                        connection, id, excludedRequestId,
                        occ.getEventDate(), occ.getStartTime(), occ.getEndTime());
                if (available < minAvailable) minAvailable = available;
            }
            if (minAvailable == Integer.MAX_VALUE) minAvailable = 0;
            equipment.put(id, new EquipmentRecord(name, status, custodianRole, minAvailable));
        }

        for (EquipmentRequestItem item : form.getEquipmentItems()) {
            EquipmentRecord record = equipment.get(item.getEquipmentId());
            if (record == null || item.getRequestedQuantity() > record.availableQuantity) {
                throw new IllegalArgumentException(
                        "Requested quantity exceeds available stock for "
                                + (record == null ? "equipment" : record.name)
                                + " on one or more selected schedules.");
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
        List<RequestOccurrence> occs = effectiveOccurrences(form);
        RequestOccurrence first = occs.get(0);
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setLong(2, borrowerId);
            statement.setString(3, first.getVenue());
            statement.setDate(4, Date.valueOf(first.getEventDate()));
            statement.setTime(5, Time.valueOf(first.getStartTime()));
            statement.setTime(6, Time.valueOf(first.getEndTime()));
            statement.setString(7, form.getPurpose());
            statement.setString(8, form.getParticipants());
            statement.setString(9, form.getPersonInCharge());
            statement.setString(10, form.getPosition());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return a request ID.");
                }
                return keys.getLong(1);
            }
        }
    }

    private List<Long> insertOccurrences(Connection connection, long requestId, BorrowRequestForm form)
            throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO request_occurrences (request_id, event_date, start_time, end_time, venue)
                VALUES (?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            for (RequestOccurrence o : effectiveOccurrences(form)) {
                statement.setLong(1, requestId);
                statement.setDate(2, Date.valueOf(o.getEventDate()));
                statement.setTime(3, Time.valueOf(o.getStartTime()));
                statement.setTime(4, Time.valueOf(o.getEndTime()));
                statement.setString(5, o.getVenue());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) ids.add(keys.getLong(1));
                }
            }
        }
        return ids;
    }

    private void bindForm(
            PreparedStatement statement,
            BorrowRequestForm form,
            int offset) throws SQLException {
        List<RequestOccurrence> occs = effectiveOccurrences(form);
        RequestOccurrence first = occs.get(0);
        statement.setString(1 + offset, first.getVenue());
        statement.setDate(2 + offset, Date.valueOf(first.getEventDate()));
        statement.setTime(3 + offset, Time.valueOf(first.getStartTime()));
        statement.setTime(4 + offset, Time.valueOf(first.getEndTime()));
        statement.setString(5 + offset, form.getPurpose());
        statement.setString(6 + offset, form.getParticipants());
        statement.setString(7 + offset, form.getPersonInCharge());
        statement.setString(8 + offset, form.getPosition());
    }

    private Map<String, Long> createGroups(
            Connection connection,
            long requestId,
            Long occurrenceId,
            Map<Long, EquipmentRecord> equipment) throws SQLException {
        Map<String, Long> groups = new HashMap<>();
        for (EquipmentRecord record : equipment.values()) {
            if (!groups.containsKey(record.custodianRole)) {
                groups.put(record.custodianRole,
                        insertGroup(connection, requestId, occurrenceId, record.custodianRole));
            }
        }
        return groups;
    }

    private long insertGroup(Connection connection, long requestId, Long occurrenceId, String role)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO request_groups (request_id, occurrence_id, custodian_role, status)
                VALUES (?, ?, ?, 'PENDING')
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, requestId);
            if (occurrenceId == null) statement.setNull(2, Types.BIGINT);
            else statement.setLong(2, occurrenceId);
            statement.setString(3, role);
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
            Long occurrenceId,
            BorrowRequestForm form,
            Map<Long, EquipmentRecord> equipment,
            Map<String, Long> groupIds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO request_items (
                    request_id, occurrence_id, group_id, requested_equipment_id, requested_quantity
                )
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (EquipmentRequestItem item : form.getEquipmentItems()) {
                EquipmentRecord record = equipment.get(item.getEquipmentId());
                Long groupId = groupIds.get(record.custodianRole);
                if (groupId == null) {
                    throw new SQLException("Equipment custodian group was not created.");
                }
                statement.setLong(1, requestId);
                if (occurrenceId == null) statement.setNull(2, Types.BIGINT);
                else statement.setLong(2, occurrenceId);
                statement.setLong(3, groupId);
                statement.setLong(4, item.getEquipmentId());
                statement.setInt(5, item.getRequestedQuantity());
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
                WHERE request_id = ? AND borrower_id = ? AND deleted_at IS NULL
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
                  AND status NOT IN ('PENDING', 'UNABLE', 'CANCELLED', 'DECLINED')
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
        private final String adminStatus;
        private final String directorStatus;
        private final LocalDateTime submittedAt;
        /** One line per equipment; multi-schedule copies are merged to avoid duplicates. */
        private final Map<Long, EquipmentRequestItem> items = new LinkedHashMap<>();
        private final List<RequestOccurrence> occurrences = new ArrayList<>();
        private final List<DepartmentStatus> departments = new ArrayList<>();
        private final String borrowerType;

        private BorrowRequestBuilder(ResultSet result) throws SQLException {
            borrowerType = result.getString("borrower_type");
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
            adminStatus = result.getString("admin_status");
            directorStatus = result.getString("director_status");
            submittedAt = result.getTimestamp("submitted_at").toLocalDateTime();
        }

        private void addItem(EquipmentRequestItem item) {
            EquipmentRequestItem existing = items.get(item.getEquipmentId());
            if (existing == null) {
                items.put(item.getEquipmentId(), item);
                return;
            }
            items.put(item.getEquipmentId(), new EquipmentRequestItem(
                    existing.getEquipmentId(), existing.getEquipmentCode(),
                    existing.getEquipmentName(), existing.getCategory(),
                    existing.getRequestedQuantity(),
                    Math.max(existing.getAssignedQuantity(), item.getAssignedQuantity()),
                    Math.max(existing.getReleasedQuantity(), item.getReleasedQuantity()),
                    Math.max(existing.getReturnedQuantity(), item.getReturnedQuantity()),
                    existing.getCustodianRole(), existing.getGroupStatus(),
                    Math.max(existing.getDamagedQuantity(), item.getDamagedQuantity()),
                    Math.max(existing.getMissingQuantity(), item.getMissingQuantity()),
                    existing.getConditionBefore() != null
                            ? existing.getConditionBefore() : item.getConditionBefore(),
                    existing.getConditionAfter() != null
                            ? existing.getConditionAfter() : item.getConditionAfter()));
        }

        private BorrowRequest build() {
            BorrowRequest draft = create(new ArrayList<>(items.values()));
            // Each item shows its department's combined status across all schedules.
            Map<String, String> roleStatus = new HashMap<>();
            List<EquipmentRequestItem> merged = new ArrayList<>();
            for (EquipmentRequestItem item : items.values()) {
                String role = item.getCustodianRole();
                String combined = role == null ? item.getGroupStatus()
                        : roleStatus.computeIfAbsent(role,
                                key -> RequestStatus.department(draft, key));
                merged.add(new EquipmentRequestItem(
                        item.getEquipmentId(), item.getEquipmentCode(),
                        item.getEquipmentName(), item.getCategory(),
                        item.getRequestedQuantity(), item.getAssignedQuantity(),
                        item.getReleasedQuantity(), item.getReturnedQuantity(),
                        role, combined == null ? item.getGroupStatus() : combined,
                        item.getDamagedQuantity(), item.getMissingQuantity(),
                        item.getConditionBefore(), item.getConditionAfter()));
            }
            return create(merged);
        }

        private BorrowRequest create(List<EquipmentRequestItem> requestItems) {
            BorrowRequest request = new BorrowRequest(
                    requestId, requestCode, borrowerId, borrowerName, venue,
                    eventDate, startTime, endTime, purpose, participants,
                    personInCharge, position, status, remarks, submittedAt,
                    requestItems, occurrences);
            request.setBorrowerType(borrowerType);
            request.setAdminStatus(adminStatus);
            request.setDirectorStatus(directorStatus);
            request.setDepartments(departments);
            return request;
        }
    }

}
