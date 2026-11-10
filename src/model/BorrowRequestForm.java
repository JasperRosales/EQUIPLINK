package src.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BorrowRequestForm {

    private final String venue;
    private final LocalDate eventDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String purpose;
    private final String participants;
    private final String personInCharge;
    private final String position;
    private final List<EquipmentRequestItem> equipmentItems;

    public BorrowRequestForm(
            String venue,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            String purpose,
            String participants,
            String personInCharge,
            String position,
            List<EquipmentRequestItem> equipmentItems) {
        this.venue = venue == null ? "" : venue.trim();
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.purpose = purpose == null ? "" : purpose.trim();
        this.participants = participants == null ? "" : participants.trim();
        this.personInCharge = personInCharge == null ? "" : personInCharge.trim();
        this.position = position == null ? "" : position.trim();
        this.equipmentItems = Collections.unmodifiableList(
                new ArrayList<>(equipmentItems == null ? List.of() : equipmentItems));
    }

    public String getVenue() { return venue; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getPurpose() { return purpose; }
    public String getParticipants() { return participants; }
    public String getPersonInCharge() { return personInCharge; }
    public String getPosition() { return position; }
    public List<EquipmentRequestItem> getEquipmentItems() { return equipmentItems; }
}
