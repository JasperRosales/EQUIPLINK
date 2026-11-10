package src.controller;

import src.model.Equipment;
import src.model.User;
import src.service.EquipmentService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    public List<Equipment> findAvailableEquipment() throws Exception {
        return equipmentService.findAvailableEquipment();
    }

    public List<Equipment> findAvailableEquipmentExcluding(long requestId)
            throws Exception {
        return equipmentService.findAvailableEquipmentExcluding(requestId);
    }

    public List<Equipment> findAvailableEquipmentForSchedule(
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            long excludedRequestId) throws Exception {
        return equipmentService.findAvailableEquipmentForSchedule(
                eventDate, startTime, endTime, excludedRequestId);
    }

    public List<Equipment> findAllEquipment() throws Exception {
        return equipmentService.findAllEquipment();
    }

    public int countAvailableEquipmentTypes() throws Exception {
        return equipmentService.countAvailableEquipmentTypes();
    }

    public int countAllEquipmentTypes() throws Exception {
        return equipmentService.countAllEquipmentTypes();
    }

    public List<Equipment> findEquipmentForCustodian(User actor) throws Exception {
        requireStaff(actor);
        return equipmentService.findEquipmentForCustodian(actor.getRole());
    }

    public void saveEquipment(
            User actor,
            Long equipmentId,
            String code,
            String name,
            String category,
            int totalQuantity,
            String status) throws Exception {
        requireStaff(actor);
        equipmentService.saveEquipment(
                actor, equipmentId, code, name, category, totalQuantity, status);
    }

    public void deleteEquipment(User actor, long equipmentId) throws Exception {
        requireStaff(actor);
        equipmentService.deleteEquipment(actor, equipmentId);
    }

    public int countEquipmentTypesForRole(String role) throws Exception {
        return equipmentService.countEquipmentTypesForRole(role);
    }

    public int countAvailableUnitsForRole(String role) throws Exception {
        return equipmentService.countAvailableUnitsForRole(role);
    }

    public int countTotalUnitsForRole(String role) throws Exception {
        return equipmentService.countTotalUnitsForRole(role);
    }

    public int countReservedUnitsToday(String role) throws Exception {
        return equipmentService.countReservedUnitsToday(role);
    }

    public int countInUseUnitsToday(String role) throws Exception {
        return equipmentService.countInUseUnitsToday(role);
    }

    private void requireStaff(User actor) {
        if (actor == null
                || (!"ICT_STAFF".equalsIgnoreCase(actor.getRole())
                        && !"FACILITY_STAFF".equalsIgnoreCase(actor.getRole()))) {
            throw new SecurityException(
                    "Only ICT or facility staff can manage equipment inventory.");
        }
    }
}
