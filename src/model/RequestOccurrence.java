package src.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class RequestOccurrence {
    private final long occurrenceId;
    private final long requestId;
    private final LocalDate eventDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String venue;

    public RequestOccurrence(long occurrenceId, long requestId, LocalDate eventDate,
            LocalTime startTime, LocalTime endTime, String venue) {
        this.occurrenceId = occurrenceId;
        this.requestId = requestId;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venue = venue == null ? "" : venue;
    }

    public RequestOccurrence(LocalDate eventDate, LocalTime startTime, LocalTime endTime, String venue) {
        this(0, 0, eventDate, startTime, endTime, venue);
    }

    public long getOccurrenceId() { return occurrenceId; }
    public long getRequestId() { return requestId; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getVenue() { return venue; }
}
