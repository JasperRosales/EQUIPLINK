package src.service;

import src.database.Database;
import src.model.PasswordResetRequest;
import src.model.User;
import src.util.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class AuthService {

    public User login(String usernameOrEmail, char[] password)
            throws Exception {

        String sql = """
                SELECT user_id, username, email, full_name,
                       department, role, borrower_type, active,
                       password_hash, password_salt,
                       password_iterations
                FROM users
                WHERE LOWER(username) = LOWER(?)
                   OR LOWER(email) = LOWER(?)
                LIMIT 1
                """;

        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, usernameOrEmail.trim());
            statement.setString(2, usernameOrEmail.trim());

            try (ResultSet rs = statement.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                if (!rs.getBoolean("active")) {
                    throw new Exception("Your account is inactive.");
                }

                String storedHash = rs.getString("password_hash");
                String salt = rs.getString("password_salt");
                int iterations = rs.getInt("password_iterations");

                boolean valid = PasswordHasher.verifyPassword(
                        password,
                        salt,
                        storedHash,
                        iterations);

                if (!valid) {
                    return null;
                }

                return new User(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("full_name"),
                        rs.getString("department"),
                        rs.getString("role"),
                        rs.getString("borrower_type"),
                        rs.getBoolean("active"));
            }
        }
    }

    public void registerBorrower(
            String username,
            String email,
            String fullName,
            String department,
            String borrowerType,
            char[] password) throws Exception {

        if (username == null || username.isBlank()
                || email == null || email.isBlank()
                || fullName == null || fullName.isBlank()
                || password == null || password.length < 8) {

            throw new IllegalArgumentException(
                    "Please complete all required fields. Password must be at least 8 characters.");
        }

        if (!"STUDENT".equalsIgnoreCase(borrowerType)
                && !"FACULTY".equalsIgnoreCase(borrowerType)) {

            throw new IllegalArgumentException(
                    "Borrower type must be STUDENT or FACULTY.");
        }

        String salt = PasswordHasher.generateSalt();

        String passwordHash = PasswordHasher.hashPassword(password, salt);

        String sql = """
                INSERT INTO users (
                    username,
                    email,
                    password_hash,
                    password_salt,
                    password_iterations,
                    role,
                    borrower_type,
                    full_name,
                    department,
                    active
                )
                VALUES (?, ?, ?, ?, ?, 'BORROWER', ?, ?, ?, TRUE)
                """;

        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username.trim());
            statement.setString(2, email.trim());
            statement.setString(3, passwordHash);
            statement.setString(4, salt);
            statement.setInt(5, PasswordHasher.getIterations());
            statement.setString(6, borrowerType.toUpperCase());
            statement.setString(7, fullName.trim());

            if (department == null || department.isBlank()) {
                statement.setNull(8, Types.VARCHAR);
            } else {
                statement.setString(8, department.trim());
            }

            statement.executeUpdate();
        }
    }

    public void changePassword(
            long userId,
            char[] currentPassword,
            char[] newPassword) throws Exception {

        if (currentPassword == null || currentPassword.length == 0
                || newPassword == null || newPassword.length < 8) {
            throw new IllegalArgumentException(
                    "The new password must be at least 8 characters.");
        }

        String selectSql = """
                SELECT password_hash, password_salt, password_iterations
                FROM users
                WHERE user_id = ?
                """;

        try (Connection connection = Database.getConnection()) {
            String storedHash;
            String salt;
            int iterations;

            try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                statement.setLong(1, userId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("User account was not found.");
                    }
                    storedHash = rs.getString("password_hash");
                    salt = rs.getString("password_salt");
                    iterations = rs.getInt("password_iterations");
                }
            }

            if (!PasswordHasher.verifyPassword(
                    currentPassword, salt, storedHash, iterations)) {
                throw new IllegalArgumentException("The current password is incorrect.");
            }

            String newSalt = PasswordHasher.generateSalt();
            String newHash = PasswordHasher.hashPassword(newPassword, newSalt);
            String updateSql = """
                    UPDATE users
                    SET password_hash = ?,
                        password_salt = ?,
                        password_iterations = ?
                    WHERE user_id = ?
                    """;

            try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                statement.setString(1, newHash);
                statement.setString(2, newSalt);
                statement.setInt(3, PasswordHasher.getIterations());
                statement.setLong(4, userId);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Password update did not complete.");
                }
            }
        }
    }

    public void requestPasswordReset(String usernameOrEmail) throws Exception {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new IllegalArgumentException("Enter your username or email.");
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long userId = null;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT user_id
                        FROM users
                        WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)
                        FOR UPDATE
                        """)) {
                    String identity = usernameOrEmail.trim();
                    statement.setString(1, identity);
                    statement.setString(2, identity);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            userId = result.getLong("user_id");
                        }
                    }
                }

                if (userId != null) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT reset_request_id
                            FROM password_reset_requests
                            WHERE user_id = ? AND status IN ('PENDING', 'APPROVED')
                            FOR UPDATE
                            """)) {
                        statement.setLong(1, userId);
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) {
                                try (PreparedStatement insert = connection.prepareStatement("""
                                        INSERT INTO password_reset_requests (user_id)
                                        VALUES (?)
                                        """)) {
                                    insert.setLong(1, userId);
                                    insert.executeUpdate();
                                }
                            }
                        }
                    }
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void resetPasswordAfterApproval(
            String usernameOrEmail,
            char[] newPassword) throws Exception {
                if (usernameOrEmail == null || usernameOrEmail.isBlank()
                        || newPassword == null || newPassword.length < 8) {
                    throw new IllegalArgumentException(
                            "Enter your username or email and a password of at least 8 characters.");
                }

                try (Connection connection = Database.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        long userId;
                        try (PreparedStatement statement = connection.prepareStatement("""
                                SELECT user_id
                                FROM users
                                WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)
                                FOR UPDATE
                                """)) {
                            String identity = usernameOrEmail.trim();
                            statement.setString(1, identity);
                            statement.setString(2, identity);
                            try (ResultSet result = statement.executeQuery()) {
                                if (!result.next()) {
                                    throw new IllegalArgumentException(
                                            "There is no approved password reset request for this account.");
                                }
                                userId = result.getLong("user_id");
                            }
                        }

                        long requestId;
                        try (PreparedStatement statement = connection.prepareStatement("""
                                SELECT reset_request_id
                                FROM password_reset_requests
                                WHERE user_id = ? AND status = 'APPROVED'
                                ORDER BY requested_at DESC
                                LIMIT 1
                                FOR UPDATE
                                """)) {
                            statement.setLong(1, userId);
                            try (ResultSet result = statement.executeQuery()) {
                                if (!result.next()) {
                                    throw new IllegalArgumentException(
                                            "There is no approved password reset request for this account.");
                                }
                                requestId = result.getLong("reset_request_id");
                            }
                        }

                        String salt = PasswordHasher.generateSalt();
                        String hash = PasswordHasher.hashPassword(newPassword, salt);
                        try (PreparedStatement statement = connection.prepareStatement("""
                                UPDATE users
                                SET password_hash = ?, password_salt = ?, password_iterations = ?
                                WHERE user_id = ?
                                """)) {
                            statement.setString(1, hash);
                            statement.setString(2, salt);
                            statement.setInt(3, PasswordHasher.getIterations());
                            statement.setLong(4, userId);
                            if (statement.executeUpdate() != 1) {
                                throw new IllegalStateException("Password update did not complete.");
                            }
                        }

                        try (PreparedStatement statement = connection.prepareStatement("""
                                UPDATE password_reset_requests
                                SET status = 'COMPLETED'
                                WHERE reset_request_id = ? AND status = 'APPROVED'
                                """)) {
                            statement.setLong(1, requestId);
                            if (statement.executeUpdate() != 1) {
                                throw new IllegalStateException(
                                        "The approved password reset could not be completed.");
                            }
                        }
                        connection.commit();
                    } catch (Exception exception) {
                        connection.rollback();
                        throw exception;
                    }
                }
            }

            public List<PasswordResetRequest> findPasswordResetRequests(String actorRole)
                    throws Exception {
                requireIctStaff(actorRole);
                String sql = """
                        SELECT pr.reset_request_id, u.user_id, u.username, u.full_name,
                               u.email, u.role, u.department, pr.requested_at
                        FROM password_reset_requests pr
                        JOIN users u ON u.user_id = pr.user_id
                        WHERE pr.status = 'PENDING'
                        ORDER BY pr.requested_at ASC, pr.reset_request_id ASC
                        """;
                List<PasswordResetRequest> requests = new ArrayList<>();
                try (Connection connection = Database.getConnection();
                        PreparedStatement statement = connection.prepareStatement(sql);
                        ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        requests.add(new PasswordResetRequest(
                                result.getLong("reset_request_id"),
                                result.getLong("user_id"),
                                result.getString("username"),
                                result.getString("full_name"),
                                result.getString("email"),
                                result.getString("role"),
                                result.getString("department"),
                                result.getTimestamp("requested_at").toLocalDateTime()));
                    }
                }
                return requests;
            }

            public void reviewPasswordReset(
                    String actorRole,
                    long actorId,
                    long requestId,
                    boolean approve,
                    String note) throws Exception {
                requireIctStaff(actorRole);
                if (requestId <= 0) {
                    throw new IllegalArgumentException("A valid reset request is required.");
                }

                String status = approve ? "APPROVED" : "REJECTED";
                try (Connection connection = Database.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        long userId;
                        try (PreparedStatement statement = connection.prepareStatement("""
                                UPDATE password_reset_requests
                                SET status = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP,
                                    review_note = ?
                                WHERE reset_request_id = ? AND status = 'PENDING'
                                RETURNING user_id
                                """)) {
                            statement.setString(1, status);
                            statement.setLong(2, actorId);
                            statement.setString(3, note == null || note.isBlank() ? null : note.trim());
                            statement.setLong(4, requestId);
                            try (ResultSet result = statement.executeQuery()) {
                                if (!result.next()) {
                                    throw new IllegalArgumentException(
                                            "This password reset request was already reviewed.");
                                }
                                userId = result.getLong("user_id");
                            }
                        }

                        String title = approve
                                ? "Password reset approved"
                                : "Password reset request declined";
                        String message = approve
                                ? "ICT approved your request. Open Forgot password on the login screen to set a new password."
                                : "ICT declined your password reset request. Contact the system administrator for help.";
                        try (PreparedStatement statement = connection.prepareStatement("""
                                INSERT INTO notifications (user_id, title, message)
                                VALUES (?, ?, ?)
                                """)) {
                            statement.setLong(1, userId);
                            statement.setString(2, title);
                            statement.setString(3, message);
                            statement.executeUpdate();
                        }
                        connection.commit();
                    } catch (Exception exception) {
                        connection.rollback();
                        throw exception;
                    }
                }
            }

            public List<User> findBorrowersForIct(String actorRole) throws Exception {
                requireIctStaff(actorRole);
                List<User> users = new ArrayList<>();
                try (Connection connection = Database.getConnection();
                        PreparedStatement statement = connection.prepareStatement("""
                                SELECT user_id, username, email, full_name, department,
                                       role, borrower_type, active
                                FROM users
                                WHERE role = 'BORROWER'
                                ORDER BY borrower_type, department NULLS LAST, full_name
                                """);
                        ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        users.add(new User(
                                result.getLong("user_id"),
                                result.getString("username"),
                                result.getString("email"),
                                result.getString("full_name"),
                                result.getString("department"),
                                result.getString("role"),
                                result.getString("borrower_type"),
                                result.getBoolean("active")));
                    }
                }
                return users;
            }

            private void requireIctStaff(String actorRole) {
                if (!"ICT_STAFF".equalsIgnoreCase(actorRole == null ? "" : actorRole.trim())) {
                    throw new SecurityException("Only ICT system administrators can manage this.");
                }
            }
}