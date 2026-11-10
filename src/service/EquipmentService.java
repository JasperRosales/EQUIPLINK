package src.service;

import src.database.Database;
import src.model.Equipment;
import src.model.User;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

public class EquipmentService {

    public List<Equipment> findAvailableEquipment() throws SQLException {
        return findEquipment(false, -1, null, null, null);
    }

    public List<Equipment> findAvailableEquipmentExcluding(long requestId) throws SQLException {
        return findEquipment(false, requestId, null, null, null);
    }

    public List<Equipment> findAvailableEquipmentForSchedule(
            java.time.LocalDate eventDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            long excludedRequestId) throws SQLException {
        if (eventDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("A date and time are required to check availability.");
        }
        return findEquipment(
                false, excludedRequestId, eventDate, startTime, endTime);
    }

    public List<Equipment> findAllEquipment() throws SQLException {
        return findEquipment(true, -1, null, null, null);
    }

    public List<Equipment> findEquipmentForCustodian(String role) throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        List<Equipment> equipment = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT e.equipment_id, e.equipment_code, e.equipment_name,
                               e.category, e.total_quantity, e.damaged_quantity,
                               e.lost_quantity, e.status, e.custodian_role,
                               GREATEST(e.total_quantity - e.damaged_quantity - e.lost_quantity
                                   - COALESCE((
                                       SELECT SUM(
                                           CASE
                                               WHEN br.status = 'RELEASED'
                                                   THEN GREATEST(ri.released_quantity - ri.returned_quantity, 0)
                                               WHEN br.status = 'APPROVED' AND ri.assigned_quantity > 0
                                                   THEN GREATEST(ri.assigned_quantity - ri.returned_quantity, 0)
                                               ELSE ri.requested_quantity
                                           END
                                       )
                                       FROM request_items ri
                                       JOIN borrow_requests br ON br.request_id = ri.request_id
                                       JOIN request_groups rg ON rg.group_id = ri.group_id
                                       WHERE ri.requested_equipment_id = e.equipment_id
                                         AND br.status IN (
                                             'PENDING_STAFF', 'NEEDS_ACTION', 'PENDING_ADMIN',
                                             'PENDING_DIRECTOR', 'APPROVED', 'RELEASED'
                                         )
                                         AND rg.status NOT IN ('UNABLE', 'CANCELLED', 'NOT_REQUIRED')
                                   ), 0), 0) AS available_quantity
                        FROM equipment e
                        WHERE e.active = TRUE AND e.custodian_role = ?
                        ORDER BY e.category, e.equipment_name, e.equipment_code
                        """)) {
            statement.setString(1, normalizedRole);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    equipment.add(mapEquipment(result));
                }
            }
        }
        return equipment;
    }

    public void saveEquipment(
            User actor,
            Long equipmentId,
            String code,
            String name,
            String category,
            int totalQuantity,
            String status) throws SQLException {
        String custodianRole = requireActorCustodian(actor);
        if (code == null || code.isBlank()
                || name == null || name.isBlank()
                || category == null || category.isBlank()) {
            throw new IllegalArgumentException(
                    "Equipment code, name, and category are required.");
        }
        if (code.trim().length() > 30 || name.trim().length() > 120
                || category.trim().length() > 60) {
            throw new IllegalArgumentException("Some equipment details exceed the allowed length.");
        }
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        if (!"AVAILABLE".equals(status)
                && !"UNDER_MAINTENANCE".equals(status)
                && !"RETIRED".equals(status)) {
            throw new IllegalArgumentException("Select a valid equipment status.");
        }

        if (equipmentId == null) {
            try (Connection connection = Database.getConnection();
                    PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO equipment (
                                equipment_code, equipment_name, category, total_quantity,
                                status, custodian_role, created_by
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """)) {
                statement.setString(1, code.trim());
                statement.setString(2, name.trim());
                statement.setString(3, category.trim());
                statement.setInt(4, totalQuantity);
                statement.setString(5, status);
                statement.setString(6, custodianRole);
                statement.setLong(7, actor.getUserId());
                statement.executeUpdate();
            }
        } else {
            try (Connection connection = Database.getConnection();
                    PreparedStatement statement = connection.prepareStatement("""
                            UPDATE equipment
                            SET equipment_code = ?, equipment_name = ?, category = ?,
                                total_quantity = ?, status = ?
                            WHERE equipment_id = ? AND custodian_role = ? AND active = TRUE
                            """)) {
                statement.setString(1, code.trim());
                statement.setString(2, name.trim());
                statement.setString(3, category.trim());
                statement.setInt(4, totalQuantity);
                statement.setString(5, status);
                statement.setLong(6, equipmentId);
                statement.setString(7, custodianRole);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalArgumentException(
                            "Equipment was not found or is outside your inventory.");
                }
            }
        }
    }

    public void deleteEquipment(User actor, long equipmentId) throws SQLException {
        String custodianRole = requireActorCustodian(actor);
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE equipment
                        SET active = FALSE
                        WHERE equipment_id = ? AND custodian_role = ? AND active = TRUE
                        """)) {
            statement.setLong(1, equipmentId);
            statement.setString(2, custodianRole);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException(
                        "Equipment was not found or is outside your inventory.");
            }
        }
    }

    public int countEquipmentTypesForRole(String role) throws SQLException {
        return findEquipmentForCustodian(role).size();
    }

    public int countAvailableUnitsForRole(String role) throws SQLException {
        int available = 0;
        for (Equipment item : findEquipmentForCustodian(role)) {
            if ("AVAILABLE".equals(item.getStatus())) {
                available += item.getAvailableQuantity();
            }
        }
        return available;
    }

    public int countTotalUnitsForRole(String role) throws SQLException {
        return countEquipmentValue(role, """
                SELECT COALESCE(SUM(total_quantity), 0)
                FROM equipment
                WHERE active = TRUE AND custodian_role = ?
                """);
    }

    public int countReservedUnitsToday(String role) throws SQLException {
        return countEquipmentValue(role, """
                SELECT COALESCE(SUM(GREATEST(ri.assigned_quantity - ri.released_quantity, 0)), 0)
                FROM request_items ri
                JOIN request_groups rg ON rg.group_id = ri.group_id
                JOIN borrow_requests br ON br.request_id = ri.request_id
                WHERE rg.custodian_role = ? AND rg.status = 'ASSIGNED'
                  AND br.status = 'APPROVED' AND br.event_date = CURRENT_DATE
                """);
    }

    public int countInUseUnitsToday(String role) throws SQLException {
        return countEquipmentValue(role, """
                SELECT COALESCE(SUM(GREATEST(ri.released_quantity - ri.returned_quantity, 0)), 0)
                FROM request_items ri
                JOIN request_groups rg ON rg.group_id = ri.group_id
                JOIN borrow_requests br ON br.request_id = ri.request_id
                WHERE rg.custodian_role = ? AND rg.status = 'RELEASED'
                  AND br.status = 'RELEASED' AND br.event_date = CURRENT_DATE
                """);
    }

    private int countEquipmentValue(String role, String sql) throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedRole);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String requireActorCustodian(User actor) {
        if (actor == null) {
            throw new SecurityException("A signed-in staff account is required.");
        }
        return requireCustodianRole(actor.getRole());
    }

    private String requireCustodianRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!"ICT_STAFF".equals(normalizedRole)
                && !"FACILITY_STAFF".equals(normalizedRole)) {
            throw new SecurityException(
                    "Only ICT or facility staff can manage equipment inventory.");
        }
        return normalizedRole;
    }

    private List<Equipment> findEquipment(
            boolean includeUnavailable,
            long excludedRequestId,
            java.time.LocalDate eventDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime) throws SQLException {
        String overlapFilter = eventDate == null ? "" : """
                                  AND (
                                      br.status = 'RELEASED'
                                      OR (
                                          br.event_date = ?
                                          AND br.start_time < ?
                                          AND br.end_time > ?
                                      )
                                  )
                """;
        String sql = """
                SELECT e.equipment_id, e.equipment_code, e.equipment_name,
                       e.category, e.total_quantity, e.damaged_quantity,
                       e.lost_quantity, e.status, e.custodian_role,
                       GREATEST(
                           e.total_quantity - e.damaged_quantity - e.lost_quantity
                           - COALESCE((
                               SELECT SUM(
                                   CASE
                                       WHEN br.status = 'RELEASED'
                                           THEN GREATEST(ri.released_quantity - ri.returned_quantity, 0)
                                       WHEN br.status = 'APPROVED' AND ri.assigned_quantity > 0
                                           THEN GREATEST(ri.assigned_quantity - ri.returned_quantity, 0)
                                       ELSE ri.requested_quantity
                                   END
                               )
                               FROM request_items ri
                               JOIN borrow_requests br ON br.request_id = ri.request_id
                               JOIN request_groups rg ON rg.group_id = ri.group_id
                               WHERE ri.requested_equipment_id = e.equipment_id
                                 AND br.status IN (
                                     'PENDING_STAFF', 'NEEDS_ACTION', 'PENDING_ADMIN',
                                     'PENDING_DIRECTOR', 'APPROVED', 'RELEASED'
                                 )
                                 AND rg.status NOT IN ('UNABLE', 'CANCELLED', 'NOT_REQUIRED')
                                 AND br.request_id <> ?
                %s
                           ), 0),
                           0
                       ) AS available_quantity
                FROM equipment e
                WHERE e.active = TRUE
                ORDER BY e.category, e.equipment_name, e.equipment_code
                """.formatted(overlapFilter);

        List<Equipment> equipment = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAvailabilityFilter(statement, excludedRequestId, eventDate, startTime, endTime);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Equipment item = mapEquipment(result);
                    if (includeUnavailable
                            || ("AVAILABLE".equals(item.getStatus())
                                    && item.getAvailableQuantity() > 0)) {
                        equipment.add(item);
                    }
                }
            }
        }
        return equipment;
    }

    public static int findAvailableQuantity(
            Connection connection,
            long equipmentId,
            Long excludedRequestId,
            java.time.LocalDate eventDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime) throws SQLException {
        String overlapFilter = eventDate == null ? "" : """
                          AND (
                              br.status = 'RELEASED'
                              OR (
                                  br.event_date = ?
                                  AND br.start_time < ?
                                  AND br.end_time > ?
                              )
                          )
                """;
        String sql = """
                SELECT GREATEST(
                    e.total_quantity - e.damaged_quantity - e.lost_quantity
                    - COALESCE((
                        SELECT SUM(
                            CASE
                                WHEN rg.status IN ('RELEASED', 'RETURNED')
                                    THEN GREATEST(ri.released_quantity - ri.returned_quantity
                                            - ri.missing_quantity, 0)
                                WHEN rg.status = 'ASSIGNED'
                                    THEN ri.assigned_quantity
                                ELSE ri.requested_quantity
                            END
                        )
                        FROM request_items ri
                        JOIN borrow_requests br ON br.request_id = ri.request_id
                        JOIN request_groups rg ON rg.group_id = ri.group_id
                        WHERE ri.requested_equipment_id = e.equipment_id
                          AND br.status IN (
                              'PENDING_STAFF', 'NEEDS_ACTION', 'PENDING_ADMIN',
                              'PENDING_DIRECTOR', 'APPROVED', 'RELEASED'
                          )
                          AND rg.status NOT IN ('UNABLE', 'CANCELLED', 'NOT_REQUIRED')
                          AND br.request_id <> ?
                %s
                    ), 0),
                    0
                ) AS available_quantity
                FROM equipment e
                WHERE e.equipment_id = ? AND e.active = TRUE
                """.formatted(overlapFilter);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int nextParameter = bindAvailabilityFilter(
                    statement,
                    excludedRequestId == null ? -1 : excludedRequestId,
                    eventDate,
                    startTime,
                    endTime);
            statement.setLong(nextParameter, equipmentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return 0;
                }
                return result.getInt("available_quantity");
            }
        }
    }

    private static int bindAvailabilityFilter(
            PreparedStatement statement,
            long excludedRequestId,
            java.time.LocalDate eventDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime) throws SQLException {
        statement.setLong(1, excludedRequestId);
        if (eventDate == null) {
            return 2;
        }
        statement.setDate(2, Date.valueOf(eventDate));
        statement.setTime(3, Time.valueOf(endTime));
        statement.setTime(4, Time.valueOf(startTime));
        return 5;
    }

    public int countAvailableEquipmentTypes() throws SQLException {
        return findAvailableEquipment().size();
    }

    public int countAllEquipmentTypes() throws SQLException {
        return findAllEquipment().size();
    }

    public int countAvailableUnits() throws SQLException {
        int available = 0;
        for (Equipment item : findAllEquipment()) {
            available += item.getAvailableQuantity();
        }
        return available;
    }

    private Equipment mapEquipment(ResultSet result) throws SQLException {
        return new Equipment(
                result.getLong("equipment_id"),
                result.getString("equipment_code"),
                result.getString("equipment_name"),
                result.getString("category"),
                result.getInt("total_quantity"),
                result.getInt("damaged_quantity"),
                result.getInt("lost_quantity"),
                result.getInt("available_quantity"),
                result.getString("status"),
                result.getString("custodian_role"));
    }
}
