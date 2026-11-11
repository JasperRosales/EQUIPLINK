package src.controller;

import src.model.PasswordResetRequest;
import src.model.User;
import src.service.AuthService;

import java.util.List;

public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // LOGIN
    public User login(String username, char[] password)
            throws Exception {

        return authService.login(username, password);
    }

    // REGISTRATION
    public void registerBorrower(
            String username,
            String email,
            String fullName,
            String department,
            String borrowerType,
            char[] password) throws Exception {

        authService.registerBorrower(
                username,
                email,
                fullName,
                department,
                borrowerType,
                password);
    }

    public void createFacultyAccount(
            User actor,
            String username,
            String email,
            String fullName,
            String department,
            char[] password) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in account is required.");
        }
        authService.createFacultyAccount(
                actor.getRole(), username, email, fullName, department, password);
    }

    public void changePassword(
            long userId,
            char[] currentPassword,
            char[] newPassword) throws Exception {

        authService.changePassword(userId, currentPassword, newPassword);
    }

    public void requestPasswordReset(String usernameOrEmail) throws Exception {
        authService.requestPasswordReset(usernameOrEmail);
    }

    public void resetPasswordAfterApproval(
            String usernameOrEmail, char[] newPassword) throws Exception {
        authService.resetPasswordAfterApproval(usernameOrEmail, newPassword);
    }

    public List<PasswordResetRequest> findPasswordResetRequests(User actor)
            throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in ICT account is required.");
        }
        return authService.findPasswordResetRequests(actor.getRole());
    }

    public void reviewPasswordReset(
            User actor, long requestId, boolean approve, String note) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in ICT account is required.");
        }
        authService.reviewPasswordReset(
                actor.getRole(), actor.getUserId(), requestId, approve, note);
    }

    public List<User> findBorrowersForIct(User actor) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in ICT account is required.");
        }
        return authService.findBorrowersForIct(actor.getRole());
    }

    public void deleteBorrowerAccount(User actor, long userId) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in ICT account is required.");
        }
        authService.deleteBorrowerAccount(actor.getRole(), actor.getUserId(), userId);
    }
}