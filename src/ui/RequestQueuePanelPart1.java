package src.ui;

import src.controller.BorrowRequestController;
import src.model.BorrowRequest;
import src.model.DepartmentStatus;
import src.model.EquipmentRequestItem;
import src.model.RequestStatus;
import src.model.User;
import src.util.UndoPolicy;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Part 1 of RequestQueuePanel, split out to keep files short. */
public abstract class RequestQueuePanelPart1 extends JPanel {

    static final DateTimeFormatter ACTION_STAMP =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    public enum Mode {
        STAFF_QUEUE,
        ADMIN_REVIEW,
        DIRECTOR_REVIEW,
        ALL_REQUESTS,
        SCHEDULE_ASSIGNMENT,
        TRANSACTION_HISTORY
    }

    User actor;
    BorrowRequestController requestController;
    Mode mode;
    final JTextField search = new JTextField(22);
    final javax.swing.JComboBox<String> dateSort = new javax.swing.JComboBox<>(
            new String[] { "Newest first", "Oldest first" });
    final src.ui.components.DateFilter dateFilter =
            new src.ui.components.DateFilter();
    final javax.swing.JComboBox<String> priorityFilter = new javax.swing.JComboBox<>(
            new String[] { "All borrowers", "Faculty (priority)", "Students" });
    final javax.swing.JComboBox<RequestStatus.Category> statusFilter =
            new javax.swing.JComboBox<>();
    final javax.swing.JComboBox<String> departmentFilter = new javax.swing.JComboBox<>(
            new String[] { "All departments", "ICT equipment", "Facility equipment" });
    final javax.swing.JComboBox<String> decisionFilter = new javax.swing.JComboBox<>(
            new String[] { "Any department decision", "Department pending", "Department approved",
                    "Department declined", "Department released", "Department returned",
                    "Department not assigned" });
    final JLabel resultCount = new JLabel(" ");
    final JPanel requestList = new JPanel();
    String scopeRole;
    List<BorrowRequest> requests = List.of();
    boolean loading;

    /** Every date with at least one request; status never affects the blue highlight. */
    Set<java.time.LocalDate> buildRequestDates() {
        Set<java.time.LocalDate> days = new HashSet<>();
        for (BorrowRequest request : requests) {
            List<src.model.RequestOccurrence> occurrences = request.getOccurrences();
            if (occurrences == null || occurrences.isEmpty()) {
                addRequestDate(days, request.getEventDate());
                continue;
            }
            for (src.model.RequestOccurrence occurrence : occurrences) {
                addRequestDate(days, occurrence.getEventDate());
            }
        }
        return days;
    }

    static void addRequestDate(Set<java.time.LocalDate> days, java.time.LocalDate date) {
        if (date != null) {
            days.add(date);
        }
    }

    boolean matchesPriority(BorrowRequest request) {
        switch (priorityFilter.getSelectedIndex()) {
            case 1:
                return request.isPriority();
            case 2:
                return !request.isPriority();
            default:
                return true;
        }
    }

    boolean matchesStatus(BorrowRequest request) {
        if (mode == Mode.SCHEDULE_ASSIGNMENT || mode == Mode.DIRECTOR_REVIEW) {
            return true;
        }
        RequestStatus.Category category =
                (RequestStatus.Category) statusFilter.getSelectedItem();
        return category == null || category.matches(request, scopeRole);
    }

    /** Matches the request when any schedule falls inside the selected date range. */
    boolean matchesDate(BorrowRequest request) {
        if (dateFilter.matches(request.getEventDate())) {
            return true;
        }
        for (src.model.RequestOccurrence occurrence : request.getOccurrences()) {
            if (dateFilter.matches(occurrence.getEventDate())) {
                return true;
            }
        }
        return false;
    }

    /** Department + department-decision filters (admin and director views). */
    boolean matchesDepartment(BorrowRequest request) {
        if (scopeRole != null) {
            return request.involvesDepartment(scopeRole);
        }
        int department = departmentFilter.getSelectedIndex();
        int decision = decisionFilter.getSelectedIndex();
        if (department <= 0 && decision <= 0) {
            return true;
        }
        String[] roles = department == 1 ? new String[] { RequestStatus.ICT }
                : department == 2 ? new String[] { RequestStatus.FACILITY }
                        : new String[] { RequestStatus.ICT, RequestStatus.FACILITY };
        for (String role : roles) {
            String status = RequestStatus.department(request, role);
            if (status != null && (decision <= 0 || decisionMatches(status, decision))) {
                return true;
            }
        }
        return false;
    }

    static boolean decisionMatches(String status, int decision) {
        switch (decision) {
            case 1: return "PENDING".equals(status) || "UNABLE".equals(status);
            case 2: return "ASSIGNED".equals(status);
            case 3: return "DECLINED".equals(status);
            case 4: return "RELEASED".equals(status);
            case 5: return "RETURNED".equals(status);
            case 6: return RequestStatus.NOT_ASSIGNED.equals(status);
            default: return true;
        }
    }

    static boolean isStaffActor(User user) {
        return user != null && ("ICT_STAFF".equalsIgnoreCase(user.getRole())
                || "FACILITY_STAFF".equalsIgnoreCase(user.getRole()));
    }

    boolean isWorkQueue() {
        return mode == Mode.STAFF_QUEUE || mode == Mode.ADMIN_REVIEW
                || mode == Mode.DIRECTOR_REVIEW || mode == Mode.SCHEDULE_ASSIGNMENT;
    }

    String searchableText(BorrowRequest request) {
        StringBuilder text = new StringBuilder()
                .append(request.getRequestCode()).append(' ')
                .append(request.getBorrowerName()).append(' ')
                .append(request.getVenue()).append(' ')
                .append(request.getPurpose()).append(' ')
                .append(request.getStatus()).append(' ')
                .append(UiSupport.displayStatus(request.getOverallStatus(scopeRole))).append(' ')
                .append(request.isPriority() ? "faculty priority" : "student");
        for (EquipmentRequestItem item : request.getEquipmentItems()) {
            text.append(' ').append(item.getEquipmentName())
                    .append(' ').append(item.getEquipmentCode());
        }
        return text.toString().toLowerCase();
    }

    /** Release, receipt, and return actors/timestamps shown on transaction cards. */
    String handoverLine(BorrowRequest request) {
        StringBuilder line = new StringBuilder();
        for (DepartmentStatus department : request.getDepartments()) {
            if (isStaffActor(actor)
                    && !actor.getRole().equalsIgnoreCase(department.getCustodianRole())) {
                continue;
            }
            String label = RequestStatus.departmentLabel(department.getCustodianRole());
            appendAction(line, label, "released", department.getReleasedByName(),
                    department.getReleasedAt());
            appendAction(line, label, "received", department.getReceivedByName(),
                    department.getReceivedAt());
            appendAction(line, label, "inspected", department.getInspectedByName(),
                    department.getInspectedAt());
        }
        return line.toString();
    }

    static void appendAction(
            StringBuilder line, String department, String action,
            String actorName, java.time.LocalDateTime at) {
        if (at == null) {
            return;
        }
        if (line.length() > 0) {
            line.append("  ·  ");
        }
        line.append(department).append(' ').append(action).append(' ')
                .append(at.format(ACTION_STAMP))
                .append(" by ").append(actorName == null ? "staff" : actorName);
    }

    boolean hasOwnGroup(BorrowRequest request, String status) {
        for (DepartmentStatus department : request.getDepartments()) {
            if (actor.getRole().equalsIgnoreCase(department.getCustodianRole())
                    && status.equals(department.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /** Release requires both review approvals and an assigned group for this department. */
    boolean canRelease(BorrowRequest request) {
        return isStaffActor(actor)
                && RequestStatus.isFinalApprovalGranted(request)
                && hasOwnGroup(request, "ASSIGNED");
    }

    /** Staff may inspect only once the borrower has confirmed receipt (RECEIVED). */
    boolean canInspectReturn(BorrowRequest request) {
        return isStaffActor(actor)
                && RequestStatus.isFinalApprovalGranted(request)
                && hasOwnGroup(request, "RECEIVED");
    }

    boolean isIctAdmin() {
        return actor != null && "ICT_STAFF".equalsIgnoreCase(actor.getRole());
    }

    String requestLabel(BorrowRequest request) {
        return request.getRequestCode() == null
                ? "#" + request.getRequestId() : request.getRequestCode();
    }

    /** Department remarks (e.g. decline reasons) visible to this view. */
    String departmentRemarks(BorrowRequest request) {
        StringBuilder text = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DepartmentStatus department : request.getDepartments()) {
            if (scopeRole != null && !scopeRole.equalsIgnoreCase(department.getCustodianRole())) {
                continue;
            }
            String remarks = department.getRemarks();
            if (remarks == null || remarks.isBlank()
                    || !seen.add(department.getCustodianRole() + remarks)) {
                continue;
            }
            if (text.length() > 0) {
                text.append("<br>");
            }
            text.append("<b>").append(RequestStatus.departmentLabel(department.getCustodianRole()))
                    .append(' ').append(UiSupport.departmentStatus(department.getStatus()).toLowerCase())
                    .append(":</b> ").append(escape(remarks));
        }
        return text.toString();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    boolean isActorItem(EquipmentRequestItem item, String groupStatus) {
        return actor.getRole().equalsIgnoreCase(item.getCustodianRole())
                && groupStatus.equals(item.getGroupStatus());
    }

    String equipmentSummary(BorrowRequest request) {
        StringBuilder summary = new StringBuilder();
        for (EquipmentRequestItem item : request.getEquipmentItems()) {
            if ((mode == Mode.STAFF_QUEUE || mode == Mode.SCHEDULE_ASSIGNMENT
                    || mode == Mode.TRANSACTION_HISTORY) && isStaffActor(actor)) {
                if (!actor.getRole().equalsIgnoreCase(item.getCustodianRole())) {
                    continue;
                }
            }
            if (summary.length() > 0) {
                summary.append("  ·  ");
            }
            summary.append(item.getEquipmentName())
                    .append(" × ").append(item.getRequestedQuantity());
            if (mode == Mode.SCHEDULE_ASSIGNMENT || mode == Mode.TRANSACTION_HISTORY) {
                summary.append(" (").append(UiSupport.displayStatus(item.getGroupStatus()))
                        .append(')');
            }
        }
        return summary.length() == 0 ? "No equipment items" : summary.toString();
    }

    JPanel message(String text, boolean error) {
        SurfacePanel panel = new SurfacePanel();
        panel.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(11));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    void showMessage(String text, boolean error) {
        requestList.removeAll();
        requestList.add(message(text, error));
        requestList.revalidate();
        requestList.repaint();
    }
}
