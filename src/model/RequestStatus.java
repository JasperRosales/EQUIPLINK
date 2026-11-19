package src.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Derives the overall request status from each department's individual progress.
 * The result is computed (never stored) so every dashboard, counter, and filter
 * always reflects the latest department decisions without stale copies.
 */
public final class RequestStatus {

    public static final String ICT = "ICT_STAFF";
    public static final String FACILITY = "FACILITY_STAFF";

    // Overall status codes.
    public static final String PENDING = "PENDING";
    public static final String PARTIALLY_APPROVED = "PARTIALLY_APPROVED";
    public static final String FULLY_APPROVED = "FULLY_APPROVED";
    public static final String PARTIALLY_RELEASED = "PARTIALLY_RELEASED";
    public static final String FULLY_RELEASED = "FULLY_RELEASED";
    public static final String COMPLETED = "COMPLETED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";
    public static final String NEEDS_ACTION = "NEEDS_ACTION";
    public static final String NOT_ASSIGNED = "NOT_ASSIGNED";
    public static final String NOT_PROCESSED = "NOT_PROCESSED";

    private RequestStatus() {
    }

    /**
     * Overall status of the request. With a custodian role, only that department's
     * equipment is considered (staff views); with null, every department counts.
     */
    public static String overall(BorrowRequest request, String scopeRole, LocalDateTime now) {
        String stage = request.getStatus();
        if ("CANCELLED".equals(stage)) return CANCELLED;
        if ("REJECTED".equals(stage)) return DECLINED;
        if ("NEEDS_ACTION".equals(stage)) return NEEDS_ACTION;
        if ("COMPLETED".equals(stage)) return COMPLETED;

        List<DepartmentStatus> groups = activeGroups(request, scopeRole);
        String overdue = overdue(request, groups, now);
        if (overdue != null) return overdue;
        return fromGroups(request, groups);
    }

    public static String overall(BorrowRequest request, String scopeRole) {
        return overall(request, scopeRole, LocalDateTime.now());
    }

    private static String overdue(
            BorrowRequest request, List<DepartmentStatus> groups, LocalDateTime now) {
        String stage = request.getStatus();
        if ("PENDING_STAFF".equals(stage)) {
            for (DepartmentStatus group : groups) {
                if (group.isNotAssignedOnTime(now)) return NOT_ASSIGNED;
            }
        }
        if ("PENDING_ADMIN".equals(stage) || "PENDING_DIRECTOR".equals(stage)) {
            LocalDateTime start = request.getFirstScheduleStart();
            if (start != null && now.isAfter(start)) return NOT_PROCESSED;
        }
        if ("APPROVED".equals(stage) || "RELEASED".equals(stage)) {
            for (DepartmentStatus group : groups) {
                if (group.isNotReleasedOnTime(now)) return NOT_PROCESSED;
            }
        }
        return null;
    }

    private static String fromGroups(BorrowRequest request, List<DepartmentStatus> groups) {
        if (groups.isEmpty()) return PENDING;
        int declined = 0, assigned = 0, released = 0, received = 0, returned = 0, pending = 0;
        for (DepartmentStatus group : groups) {
            switch (group.getStatus()) {
                case "DECLINED" -> declined++;
                case "ASSIGNED" -> assigned++;
                case "RELEASED" -> released++;
                case "RECEIVED" -> received++;
                case "RETURNED" -> returned++;
                default -> pending++;
            }
        }
        int total = groups.size();
        if (declined == total) return DECLINED;
        if (returned > 0 && returned == total - declined) return COMPLETED;
        if (released + received + returned > 0) {
            return released + received + returned == total
                    ? FULLY_RELEASED : PARTIALLY_RELEASED;
        }
        if (assigned > 0) {
            // Department acceptance alone is only the first review stage. A request
            // stays Pending (Awaiting Campus Director Approval) until final approval.
            if (assigned == total && !isFinalApprovalGranted(request)) return PENDING;
            return assigned == total ? FULLY_APPROVED : PARTIALLY_APPROVED;
        }
        return PENDING;
    }

    /** True only after the Campus Director's approval (or a completed workflow). */
    public static boolean isFinalApprovalGranted(BorrowRequest request) {
        if (request == null) return false;
        String stage = request.getStatus();
        return "APPROVED".equals(stage) || "RELEASED".equals(stage)
                || "COMPLETED".equals(stage)
                || "APPROVED".equalsIgnoreCase(request.getDirectorStatus());
    }

    /** Groups still taking part in the request, optionally limited to one department. */
    public static List<DepartmentStatus> activeGroups(BorrowRequest request, String scopeRole) {
        List<DepartmentStatus> groups = new ArrayList<>();
        for (DepartmentStatus group : request.getDepartments()) {
            if (scopeRole != null && !scopeRole.equalsIgnoreCase(group.getCustodianRole())) continue;
            String status = group.getStatus();
            if ("CANCELLED".equals(status) || "NOT_REQUIRED".equals(status)) continue;
            groups.add(group);
        }
        return groups;
    }

    /**
     * One department's combined status across all its schedules, or null when the
     * request has no equipment for that department. Overdue pending work reports
     * NOT_ASSIGNED.
     */
    public static String department(BorrowRequest request, String role, LocalDateTime now) {
        List<DepartmentStatus> all = new ArrayList<>();
        for (DepartmentStatus group : request.getDepartments()) {
            if (role.equalsIgnoreCase(group.getCustodianRole())) all.add(group);
        }
        if (all.isEmpty()) return null;
        boolean anyPending = false, anyAssigned = false, anyReleased = false,
                anyReceived = false, anyReturned = false, anyDeclined = false,
                anyUnable = false, overdue = false;
        for (DepartmentStatus group : all) {
            switch (group.getStatus()) {
                case "PENDING" -> {
                    anyPending = true;
                    if ("PENDING_STAFF".equals(request.getStatus()) && group.isNotAssignedOnTime(now)) {
                        overdue = true;
                    }
                }
                case "ASSIGNED" -> anyAssigned = true;
                case "RELEASED" -> anyReleased = true;
                case "RECEIVED" -> anyReceived = true;
                case "RETURNED" -> anyReturned = true;
                case "DECLINED" -> anyDeclined = true;
                case "UNABLE" -> anyUnable = true;
                default -> { }
            }
        }
        if (overdue) return NOT_ASSIGNED;
        if (anyPending) return "PENDING";
        if (anyUnable) return "UNABLE";
        if (anyAssigned) return "ASSIGNED";
        if (anyReleased) return "RELEASED";
        if (anyReceived) return "RECEIVED";
        if (anyReturned) return "RETURNED";
        if (anyDeclined) return "DECLINED";
        return all.get(0).getStatus();
    }

    public static String department(BorrowRequest request, String role) {
        return department(request, role, LocalDateTime.now());
    }

    public static boolean isOverdue(String overall) {
        return NOT_ASSIGNED.equals(overall) || NOT_PROCESSED.equals(overall);
    }

    /**
     * Human-readable approval progress across all three required stages:
     * department processing, Facility Admin review, and Campus Director review.
     */
    public static String approvalProgress(BorrowRequest request, String scopeRole) {
        if (request == null) return "—";
        List<DepartmentStatus> groups = activeGroups(request, scopeRole);
        int done = 0;
        for (DepartmentStatus group : groups) {
            String status = group.getStatus();
            if (!"PENDING".equals(status) && !"UNABLE".equals(status)
                    && !"NOT_ASSIGNED".equals(status)) {
                done++;
            }
        }
        String departments = groups.isEmpty()
                ? "—" : done + " of " + groups.size();
        return "Departments " + departments
                + "  ·  Facility Admin: " + reviewLabel(request, request.getAdminStatus(),
                        "PENDING_ADMIN")
                + "  ·  Campus Director: " + reviewLabel(request, request.getDirectorStatus(),
                        "PENDING_DIRECTOR");
    }

    private static String reviewLabel(BorrowRequest request, String decision, String pendingStage) {
        if ("APPROVED".equalsIgnoreCase(decision)) return "Approved";
        if ("REJECTED".equalsIgnoreCase(decision)) return "Declined";
        if (pendingStage.equals(request.getStatus())) {
            return "PENDING_ADMIN".equals(pendingStage)
                    ? "Awaiting Facility Admin Approval"
                    : "Awaiting Campus Director Approval";
        }
        return "Not started";
    }

    public static String departmentLabel(String role) {
        if (ICT.equalsIgnoreCase(role)) return "ICT";
        if (FACILITY.equalsIgnoreCase(role)) return "Facility";
        return role == null ? "—" : role;
    }

    /** Status categories shared by every status filter and dashboard counter. */
    public enum Category {
        ALL("All statuses", (r, s) -> true),
        PENDING("Pending", (r, s) -> is(r, s, RequestStatus.PENDING)),
        APPROVED("Approved", (r, s) -> is(r, s, RequestStatus.PARTIALLY_APPROVED, RequestStatus.FULLY_APPROVED)),
        PARTIALLY_APPROVED("Partially approved", (r, s) -> is(r, s, RequestStatus.PARTIALLY_APPROVED)),
        FULLY_APPROVED("Fully approved", (r, s) -> is(r, s, RequestStatus.FULLY_APPROVED)),
        PARTIALLY_RELEASED("Partially released", (r, s) -> is(r, s, RequestStatus.PARTIALLY_RELEASED)),
        FULLY_RELEASED("Fully released", (r, s) -> is(r, s, RequestStatus.FULLY_RELEASED)),
        COMPLETED("Completed", (r, s) -> is(r, s, RequestStatus.COMPLETED)),
        DECLINED("Declined", (r, s) -> is(r, s, RequestStatus.DECLINED)),
        OVERDUE("Overdue / not processed", (r, s) -> is(r, s, NOT_ASSIGNED, NOT_PROCESSED)),
        NEEDS_ACTION("Needs action", (r, s) -> is(r, s, RequestStatus.NEEDS_ACTION)),
        CANCELLED("Cancelled", (r, s) -> is(r, s, RequestStatus.CANCELLED)),
        ADMIN_REVIEW("Awaiting Facility Admin Approval", (r, s) -> "PENDING_ADMIN".equals(r.getStatus())),
        DIRECTOR_REVIEW("Awaiting Campus Director Approval", (r, s) -> "PENDING_DIRECTOR".equals(r.getStatus())),
        FINAL_APPROVED("Final approval granted",
                (r, s) -> isFinalApprovalGranted(r));

        private final String label;
        private final Matcher matcher;

        Category(String label, Matcher matcher) {
            this.label = label;
            this.matcher = matcher;
        }

        public String getLabel() { return label; }

        public boolean matches(BorrowRequest request, String scopeRole) {
            return matcher.matches(request, scopeRole);
        }

        public Predicate<BorrowRequest> predicate(String scopeRole) {
            return request -> matches(request, scopeRole);
        }

        public static Category fromLabel(String label) {
            for (Category category : values()) {
                if (category.label.equalsIgnoreCase(label) || category.name().equalsIgnoreCase(label)) {
                    return category;
                }
            }
            return ALL;
        }

        public static String[] labels() {
            Category[] values = values();
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i].label;
            return labels;
        }

        public long count(List<BorrowRequest> requests, String scopeRole) {
            return requests.stream().filter(predicate(scopeRole)).count();
        }

        private static boolean is(BorrowRequest request, String scopeRole, String... codes) {
            String overall = overall(request, scopeRole);
            for (String code : codes) {
                if (code.equals(overall)) return true;
            }
            return false;
        }

        @Override
        public String toString() { return label; }
    }

    @FunctionalInterface
    private interface Matcher {
        boolean matches(BorrowRequest request, String scopeRole);
    }
}
