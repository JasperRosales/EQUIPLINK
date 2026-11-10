package src.model;

import java.time.LocalDateTime;

public class Notification {

    private final long notificationId;
    private final long userId;
    private final Long requestId;
    private final String title;
    private final String message;
    private final boolean read;
    private final LocalDateTime createdAt;

    public Notification(
            long notificationId,
            long userId,
            Long requestId,
            String title,
            String message,
            boolean read,
            LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.requestId = requestId;
        this.title = title;
        this.message = message;
        this.read = read;
        this.createdAt = createdAt;
    }

    public long getNotificationId() { return notificationId; }
    public long getUserId() { return userId; }
    public Long getRequestId() { return requestId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
