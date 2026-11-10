package src.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Notification {

    private final long notificationId;
    private final long userId;
    private final Long requestId;
    private final String title;
    private final String message;
    private final boolean read;
    private final LocalDateTime createdAt;
    private final String requestEquipment;
    private final String requestCategories;
    private final LocalDate eventDate;

    public Notification(
            long notificationId,
            long userId,
            Long requestId,
            String title,
            String message,
            boolean read,
            LocalDateTime createdAt) {
        this(notificationId, userId, requestId, title, message, read, createdAt,
                "", "", null);
    }

    public Notification(
            long notificationId,
            long userId,
            Long requestId,
            String title,
            String message,
            boolean read,
            LocalDateTime createdAt,
            String requestEquipment,
            String requestCategories,
            LocalDate eventDate) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.requestId = requestId;
        this.title = title;
        this.message = message;
        this.read = read;
        this.createdAt = createdAt;
        this.requestEquipment = requestEquipment == null ? "" : requestEquipment;
        this.requestCategories = requestCategories == null ? "" : requestCategories;
        this.eventDate = eventDate;
    }

    public long getNotificationId() { return notificationId; }
    public long getUserId() { return userId; }
    public Long getRequestId() { return requestId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getRequestEquipment() { return requestEquipment; }
    public String getRequestCategories() { return requestCategories; }
    public LocalDate getEventDate() { return eventDate; }
}
