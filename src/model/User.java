package src.model;

public class User {

    private long userId;
    private String username;
    private String email;
    private String fullName;
    private String department;
    private String role;
    private String borrowerType;
    private boolean active;

    public User(long userId, String username, String email,
            String fullName, String department,
            String role, String borrowerType, boolean active) {

        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.department = department;
        this.role = role;
        this.borrowerType = borrowerType;
        this.active = active;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDepartment() {
        return department;
    }

    public String getRole() {
        return role;
    }

    public String getBorrowerType() {
        return borrowerType;
    }

    public boolean isActive() {
        return active;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setBorrowerType(String borrowerType) {
        this.borrowerType = borrowerType;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}