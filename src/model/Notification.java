package src.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

public class Notification {

    public static final String CATEGORY_REQUEST = "REQUEST";
    public static final String CATEGORY_BORROWING_RETURNS = "BORROWING_RETURNS";
    public static final String CATEGORY_ACCOUNT = "ACCOUNT";

    /** Titles (or title endings) that belong to borrowing, release, and return updates. */
    private static final Set<String> BORROWING_TITLES = Set.of(
            "equipment released",
            "equipment returned",
            "released equipment",
            "returned equipment",
            "borrower confirmed receipt",
            "release not processed",
            "transaction completed",
            "damaged or missing equipment",
            "equipment ready for release",
            "upcoming equipment release",
            "equipment release reminder",
            "not released on time",
            "ready for return inspection",
            "awaiting inspection");

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
    private final String category;
    private String requestCode;
    private String department;
    private String action;
    private String requestStatus;

    public Notification(
            long notificationId,
            long userId,
            Long requestId,
            String title,
            String message,
            boolean read,
            LocalDateTime createdAt) {
        this(notificationId, userId, requestId, title, message, read, createdAt,
                "", "", null, null);
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
        this(notificationId, userId, requestId, title, message, read, createdAt,
                requestEquipment, requestCategories, eventDate, null);
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
            LocalDate eventDate,
            String category) {
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
        this.category = category == null || category.isBlank()
                ? categoryFor(title) : category;
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
    public String getCategory() { return category; }
    public String getRequestCode() { return requestCode; }
    public String getDepartment() { return department; }
    public String getAction() { return action; }
    public String getRequestStatus() { return requestStatus; }

    public String getCategoryLabel() {
        switch (category) {
            case CATEGORY_BORROWING_RETURNS:
                return "Borrowing & Returns";
            case CATEGORY_ACCOUNT:
                return "Account";
            default:
                return "Request";
        }
    }

    /**
     * Classifies a notification by its title. This keeps older rows (created before
     * categories existed) in the right bucket without a database migration.
     */
    public static String categoryFor(String title) {
        if (title == null) {
            return CATEGORY_REQUEST;
        }
        String normalized = title.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("password reset")) {
            return CATEGORY_ACCOUNT;
        }
        for (String borrowing : BORROWING_TITLES) {
            // Department names are prefixed to some titles, e.g. "ICT equipment released".
            if (normalized.equals(borrowing) || normalized.endsWith(" " + borrowing)) {
                return CATEGORY_BORROWING_RETURNS;
            }
        }
        return CATEGORY_REQUEST;
    }

    /** Adds the related request reference and the department/action that caused it. */
    public Notification withDetails(
            String requestCode, String department, String action, String requestStatus) {
        this.requestCode = requestCode;
        this.department = department;
        this.action = action;
        this.requestStatus = requestStatus;
        return this;
    }
}
