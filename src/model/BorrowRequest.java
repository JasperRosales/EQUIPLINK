package src.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BorrowRequest {

    private final long requestId;
    private final String requestCode;
    private final long borrowerId;
    private final String borrowerName;
    private final String venue;
    private final LocalDate eventDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String purpose;
    private final String participants;
    private final String personInCharge;
    private final String position;
    private final String status;
    private final String remarks;
    private final LocalDateTime submittedAt;
    private final List<EquipmentRequestItem> equipmentItems;
    private String borrowerType;

    /** Faculty first, then everything else; stable for other orderings. */
    public static final java.util.Comparator<BorrowRequest> PRIORITY_FIRST =
            java.util.Comparator.comparing(request -> !request.isPriority());

    public BorrowRequest(
            long requestId,
            String requestCode,
            long borrowerId,
            String borrowerName,
            String venue,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            String purpose,
            String participants,
            String personInCharge,
            String position,
            String status,
            String remarks,
            LocalDateTime submittedAt,
            List<EquipmentRequestItem> equipmentItems) {
        this.requestId = requestId;
        this.requestCode = requestCode;
        this.borrowerId = borrowerId;
        this.borrowerName = borrowerName;
        this.venue = venue;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.purpose = purpose;
        this.participants = participants;
        this.personInCharge = personInCharge;
        this.position = position;
        this.status = status;
        this.remarks = remarks;
        this.submittedAt = submittedAt;
        this.equipmentItems = Collections.unmodifiableList(
                new ArrayList<>(equipmentItems == null ? List.of() : equipmentItems));
    }

    public long getRequestId() { return requestId; }
    public String getRequestCode() { return requestCode; }
    public long getBorrowerId() { return borrowerId; }
    public String getBorrowerName() { return borrowerName; }
    public String getVenue() { return venue; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getPurpose() { return purpose; }
    public String getParticipants() { return participants; }
    public String getPersonInCharge() { return personInCharge; }
    public String getPosition() { return position; }
    public String getStatus() { return status; }
    public String getRemarks() { return remarks; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public List<EquipmentRequestItem> getEquipmentItems() { return equipmentItems; }

    public String getBorrowerType() {
        return borrowerType;
    }

    public void setBorrowerType(String borrowerType) {
        this.borrowerType = borrowerType;
    }

    /** Faculty requests are processed with priority. */
    public boolean isPriority() {
        return "FACULTY".equalsIgnoreCase(borrowerType);
    }
}
