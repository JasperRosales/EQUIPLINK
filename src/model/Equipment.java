package src.model;

public class Equipment {

    private final long equipmentId;
    private final String equipmentCode;
    private final String equipmentName;
    private final String category;
    private final int totalQuantity;
    private final int damagedQuantity;
    private final int lostQuantity;
    private final int availableQuantity;
    private final String status;
    private final String custodianRole;

    public Equipment(
            long equipmentId,
            String equipmentCode,
            String equipmentName,
            String category,
            int totalQuantity,
            int damagedQuantity,
            int lostQuantity,
            int availableQuantity,
            String status,
            String custodianRole) {

        this.equipmentId = equipmentId;
        this.equipmentCode = equipmentCode;
        this.equipmentName = equipmentName;
        this.category = category;
        this.totalQuantity = totalQuantity;
        this.damagedQuantity = damagedQuantity;
        this.lostQuantity = lostQuantity;
        this.availableQuantity = availableQuantity;
        this.status = status;
        this.custodianRole = custodianRole;
    }

    public long getEquipmentId() { return equipmentId; }
    public String getEquipmentCode() { return equipmentCode; }
    public String getEquipmentName() { return equipmentName; }
    public String getCategory() { return category; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getDamagedQuantity() { return damagedQuantity; }
    public int getLostQuantity() { return lostQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
    public String getStatus() { return status; }
    public String getCustodianRole() { return custodianRole; }
}
