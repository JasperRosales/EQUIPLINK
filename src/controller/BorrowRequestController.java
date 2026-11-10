package src.controller;

import src.model.BorrowRequest;
import src.model.BorrowRequestForm;
import src.model.User;
import src.service.BorrowRequestService;

import java.util.List;

public class BorrowRequestController {

    private final BorrowRequestService requestService;

    public BorrowRequestController(BorrowRequestService requestService) {
        this.requestService = requestService;
    }

    public List<BorrowRequest> findActiveRequests(long borrowerId) throws Exception {
        return requestService.findActiveRequests(borrowerId);
    }

    public List<BorrowRequest> findBorrowingHistory(long borrowerId) throws Exception {
        return requestService.findBorrowingHistory(borrowerId);
    }

    public BorrowRequest findRequest(long borrowerId, long requestId) throws Exception {
        return requestService.findRequest(borrowerId, requestId);
    }

    public long createRequest(long borrowerId, BorrowRequestForm form) throws Exception {
        return requestService.createRequest(borrowerId, form);
    }

    public void updateRequest(
            long borrowerId, long requestId, BorrowRequestForm form) throws Exception {
        requestService.updateRequest(borrowerId, requestId, form);
    }

    public void deleteRequest(long borrowerId, long requestId) throws Exception {
        requestService.deleteRequest(borrowerId, requestId);
    }

    public int countActiveForBorrower(long borrowerId) throws Exception {
        return requestService.countActiveForBorrower(borrowerId);
    }

    public int countPendingForBorrower(long borrowerId) throws Exception {
        return requestService.countPendingForBorrower(borrowerId);
    }

    public int countBorrowedForBorrower(long borrowerId) throws Exception {
        return requestService.countBorrowedForBorrower(borrowerId);
    }

    public int countQueueForRole(String role) throws Exception {
        return requestService.countQueueForRole(role);
    }

    public int countByStatus(String status) throws Exception {
        return requestService.countByStatus(status);
    }

    public List<BorrowRequest> findStaffQueue(User actor) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        return requestService.findStaffQueue(actor.getRole());
    }

    public List<BorrowRequest> findAdminQueue(User actor) throws Exception {
        requireRole(actor, "ADMIN");
        return requestService.findAdminQueue();
    }

    public List<BorrowRequest> findDirectorQueue(User actor) throws Exception {
        requireRole(actor, "DIRECTOR");
        return requestService.findDirectorQueue();
    }

    public List<BorrowRequest> findAllRequests(User actor) throws Exception {
        requireRole(actor, "ICT_STAFF", "ADMIN", "DIRECTOR");
        return requestService.findAllRequests();
    }

    public List<BorrowRequest> findTransactionsForRole(User actor) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        return requestService.findTransactionsForRole(actor.getRole());
    }

    public void assignStaffRequest(User actor, long requestId) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.assignStaffRequest(actor.getRole(), actor.getUserId(), requestId);
    }

    public void markStaffRequestNeedsAction(
            User actor, long requestId, String remarks) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.markStaffRequestNeedsAction(
                actor.getRole(), actor.getUserId(), requestId, remarks);
    }

    public void reviewByAdmin(
            User actor, long requestId, boolean approve, String remarks) throws Exception {
        requireRole(actor, "ADMIN");
        requestService.reviewByAdmin(actor.getUserId(), requestId, approve, remarks);
    }

    public void reviewByDirector(
            User actor, long requestId, boolean approve, String remarks) throws Exception {
        requireRole(actor, "DIRECTOR");
        requestService.reviewByDirector(actor.getUserId(), requestId, approve, remarks);
    }

    public void releaseStaffAssignment(User actor, long requestId) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.releaseStaffAssignment(actor.getRole(), actor.getUserId(), requestId);
    }

    public void recordStaffReturn(User actor, long requestId) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.recordStaffReturn(actor.getRole(), actor.getUserId(), requestId);
    }

    private void requireRole(User actor, String... allowedRoles) {
        if (actor == null) {
            throw new SecurityException("A signed-in account is required.");
        }
        for (String role : allowedRoles) {
            if (role.equalsIgnoreCase(actor.getRole())) {
                return;
            }
        }
        throw new SecurityException("Your account is not allowed to perform this action.");
    }
}
