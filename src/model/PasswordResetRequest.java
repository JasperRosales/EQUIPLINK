package src.model;

import java.time.LocalDateTime;

public class PasswordResetRequest {

    private final long requestId;
    private final long userId;
    private final String username;
    private final String fullName;
    private final String email;
    private final String role;
    private final String department;
    private final LocalDateTime requestedAt;

    public PasswordResetRequest(
            long requestId,
            long userId,
            String username,
            String fullName,
            String email,
            String role,
            String department,
            LocalDateTime requestedAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.department = department;
        this.requestedAt = requestedAt;
    }

    public long getRequestId() { return requestId; }
    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getDepartment() { return department; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
}
