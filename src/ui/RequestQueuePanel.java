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

public class RequestQueuePanel extends JPanel implements Refreshable {

    public enum Mode {
        STAFF_QUEUE,
        ADMIN_REVIEW,
        DIRECTOR_REVIEW,
        ALL_REQUESTS,
        SCHEDULE_ASSIGNMENT,
        TRANSACTION_HISTORY
    }

    private final User actor;
    private final BorrowRequestController requestController;
    private final Mode mode;
    private final JTextField search = new JTextField(22);
    private final javax.swing.JComboBox<String> dateSort = new javax.swing.JComboBox<>(
            new String[] { "Newest first", "Oldest first" });
    private final src.ui.components.DateFilter dateFilter =
            new src.ui.components.DateFilter();
    private final javax.swing.JComboBox<String> priorityFilter = new javax.swing.JComboBox<>(
            new String[] { "All borrowers", "Faculty (priority)", "Students" });
    private final javax.swing.JComboBox<RequestStatus.Category> statusFilter =
            new javax.swing.JComboBox<>();
    private final javax.swing.JComboBox<String> departmentFilter = new javax.swing.JComboBox<>(
            new String[] { "All departments", "ICT equipment", "Facility equipment" });
    private final javax.swing.JComboBox<String> decisionFilter = new javax.swing.JComboBox<>(
            new String[] { "Any department decision", "Department pending", "Department approved",
                    "Department declined", "Department released", "Department returned",
                    "Department not assigned" });
    private final JLabel resultCount = new JLabel(" ");
    private final JPanel requestList = new JPanel();
    private final String scopeRole;
    private List<BorrowRequest> requests = List.of();
    private boolean loading;

    public RequestQueuePanel(
            User actor, BorrowRequestController requestController, Mode mode) {
        this(actor, requestController, mode, null);
    }

    /** @param initialFilter status category label to preselect, e.g. "Declined". */
    public RequestQueuePanel(
            User actor, BorrowRequestController requestController, Mode mode,
            String initialFilter) {
        this.actor = actor;
        this.requestController = requestController;
        this.mode = mode;
        this.scopeRole = isStaffActor(actor) ? actor.getRole().toUpperCase() : null;
        statusFilter.setModel(new javax.swing.DefaultComboBoxModel<>(
                statusCategoriesFor(mode)));
        if (initialFilter != null) {
            RequestStatus.Category initial = RequestStatus.Category.fromLabel(initialFilter);
            statusFilter.setSelectedItem(
                    statusCategoriesContains(mode, initial) ? initial : RequestStatus.Category.ALL);
        }
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        addHeader();
        requestList.setOpaque(false);
        requestList.setLayout(new BoxLayout(requestList, BoxLayout.Y_AXIS));
        add(requestList);
        loadRequests();
    }

    private void addHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(titleForMode());
        title.setFont(AppTheme.semibold(18));
        title.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel(subtitleForMode());
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        header.add(text, BorderLayout.NORTH);

        JPanel filters = new JPanel(new BorderLayout(10, 0));
        // Search + date tools on the first row, status/department filters below.
        filters.setOpaque(false);
        UiSupport.styleInput(search);
        search.putClientProperty("JTextField.placeholderText",
                "Search by code, borrower, venue, or equipment");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(UiSupport.documentListener(this::showRequests));
        filters.add(search, BorderLayout.CENTER);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        dateSort.setFont(AppTheme.regular(12));
        dateSort.setPreferredSize(new java.awt.Dimension(150, 40));
        dateSort.addActionListener(event -> showRequests());
        dateFilter.addChangeListener(this::showRequests);
        tools.add(dateFilter);
        tools.add(dateSort);
        PrimaryButton refresh = new PrimaryButton("Refresh", PrimaryButton.Style.SECONDARY);
        refresh.setToolTipText("Reload requests and return the calendar to today");
        refresh.addActionListener(event -> {
            dateFilter.resetToToday();
            loadRequests(false);
        });
        tools.add(refresh);
        filters.add(tools, BorderLayout.EAST);

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusRow.setOpaque(false);
        if (mode != Mode.SCHEDULE_ASSIGNMENT && mode != Mode.DIRECTOR_REVIEW) {
            styleCombo(statusFilter, 200, "Filter by overall request status");
            statusRow.add(statusFilter);
        }
        if (mode == Mode.ALL_REQUESTS
                || (mode == Mode.TRANSACTION_HISTORY && !isStaffActor(actor))) {
            // Admin/Director transaction history keeps the department/decision tools
            // from the former All Requests page; staff history stays department-scoped.
            styleCombo(departmentFilter, 165, "Filter by custodian department");
            styleCombo(decisionFilter, 225, "Filter by the department's own decision");
            statusRow.add(departmentFilter);
            statusRow.add(decisionFilter);
        } else if (mode == Mode.ADMIN_REVIEW) {
            styleCombo(decisionFilter, 225, "Filter by the department's own decision");
            statusRow.add(decisionFilter);
        }
        styleCombo(priorityFilter, 165, "Filter by borrower type");
        statusRow.add(priorityFilter);
        resultCount.setFont(AppTheme.semibold(11));
        resultCount.setForeground(AppTheme.primary());
        statusRow.add(resultCount);
        JPanel filterBlock = new JPanel(new BorderLayout(0, 8));
        filterBlock.setOpaque(false);
        filterBlock.add(filters, BorderLayout.NORTH);
        filterBlock.add(statusRow, BorderLayout.CENTER);
        filters = filterBlock;
        header.add(filters, BorderLayout.CENTER);
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(14));
    }

    private void styleCombo(javax.swing.JComboBox<?> combo, int width, String tooltip) {
        combo.setFont(AppTheme.regular(12));
        combo.setPreferredSize(new java.awt.Dimension(width, 40));
        combo.setToolTipText(tooltip);
        combo.addActionListener(event -> showRequests());
    }

    @Override
    public void reloadData() {
        loadRequests(true);
    }

    private void loadRequests() {
        loadRequests(false);
    }

    private void loadRequests(boolean quiet) {
        if (loading) {
            return;
        }
        loading = true;
        if (!quiet) {
            showMessage("Loading requests…", false);
        }
        new SwingWorker<List<BorrowRequest>, Void>() {
            @Override
            protected List<BorrowRequest> doInBackground() throws Exception {
                switch (mode) {
                    case STAFF_QUEUE:
                        return requestController.findStaffQueue(actor);
                    case ADMIN_REVIEW:
                        return requestController.findAdminQueue(actor);
                    case DIRECTOR_REVIEW:
                        return requestController.findDirectorQueue(actor);
                    case SCHEDULE_ASSIGNMENT:
                        return requestController.findScheduleForRole(actor);
                    case TRANSACTION_HISTORY:
                        return isStaffActor(actor)
                                ? requestController.findHistoryForRole(actor)
                                : requestController.findAllRequests(actor);
                    case ALL_REQUESTS:
                    default:
                        return requestController.findAllRequests(actor);
                }
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    requests = get();
                    showRequests();
                } catch (Exception exception) {
                    showMessage("Unable to load requests: " + messageOf(exception), true);
                }
            }
        }.execute();
    }

    private void showRequests() {
        requestList.removeAll();
        dateFilter.setRequestDays(buildRequestDates());
        String query = search.getText().trim().toLowerCase();
        List<BorrowRequest> filtered = new ArrayList<>();
        for (BorrowRequest request : requests) {
            if (matchesDate(request)
                    && matchesPriority(request)
                    && matchesStatus(request)
                    && matchesDepartment(request)
                    && searchableText(request).contains(query)) {
                filtered.add(request);
            }
        }
        Comparator<BorrowRequest> byDate = Comparator.comparing(BorrowRequest::getSubmittedAt);
        if (dateSort.getSelectedIndex() == 0) {
            byDate = byDate.reversed();
        }
        // Work queues serve faculty requests first; record views keep plain date order.
        filtered.sort(isWorkQueue() ? BorrowRequest.PRIORITY_FIRST.thenComparing(byDate) : byDate);

        resultCount.setText(filtered.size() + " of " + requests.size() + " shown");
        if (filtered.isEmpty()) {
            requestList.add(message(
                    requests.isEmpty()
                            ? emptyMessage()
                            : "No requests match the current search or filters.",
                    false));
        } else {
            for (BorrowRequest request : filtered) {
                requestList.add(requestCard(request));
                requestList.add(Box.createVerticalStrut(9));
            }
        }
        requestList.revalidate();
        requestList.repaint();
    }

    /** Every date with at least one request; status never affects the blue highlight. */
    private Set<java.time.LocalDate> buildRequestDates() {
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

    private static void addRequestDate(Set<java.time.LocalDate> days, java.time.LocalDate date) {
        if (date != null) {
            days.add(date);
        }
    }

    private boolean matchesPriority(BorrowRequest request) {
        switch (priorityFilter.getSelectedIndex()) {
            case 1:
                return request.isPriority();
            case 2:
                return !request.isPriority();
            default:
                return true;
        }
    }

    private boolean matchesStatus(BorrowRequest request) {
        if (mode == Mode.SCHEDULE_ASSIGNMENT || mode == Mode.DIRECTOR_REVIEW) {
            return true;
        }
        RequestStatus.Category category =
                (RequestStatus.Category) statusFilter.getSelectedItem();
        return category == null || category.matches(request, scopeRole);
    }

    /** Matches the request when any schedule falls inside the selected date range. */
    private boolean matchesDate(BorrowRequest request) {
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
    private boolean matchesDepartment(BorrowRequest request) {
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

    private static boolean decisionMatches(String status, int decision) {
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

    private static boolean isStaffActor(User user) {
        return user != null && ("ICT_STAFF".equalsIgnoreCase(user.getRole())
                || "FACILITY_STAFF".equalsIgnoreCase(user.getRole()));
    }

    private boolean isWorkQueue() {
        return mode == Mode.STAFF_QUEUE || mode == Mode.ADMIN_REVIEW
                || mode == Mode.DIRECTOR_REVIEW || mode == Mode.SCHEDULE_ASSIGNMENT;
    }

    private String searchableText(BorrowRequest request) {
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

    private JPanel requestCard(BorrowRequest request) {
        final float[] hover = {0f};
        SurfacePanel card = new SurfacePanel() {
            @Override
            protected void paintBorder(java.awt.Graphics graphics) {
                super.paintBorder(graphics);
                if (hover[0] <= 0f) return;
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.alpha(AppTheme.PRIMARY, Math.round(255 * hover[0])));
                g.setStroke(new java.awt.BasicStroke(2f));
                int m = SurfacePanel.MARGIN;
                g.drawRoundRect(m, m, getWidth() - m * 2 - 1, getHeight() - m * 2 - 1, 18, 18);
                g.dispose();
            }
        };
        javax.swing.Timer hoverTimer = new javax.swing.Timer(15, null);
        hoverTimer.addActionListener(event -> {
            boolean inside = card.getMousePosition() != null;
            float target = inside ? 1f : 0f, step = 15f / 200f;
            if (Math.abs(hover[0] - target) <= step) { hover[0] = target; hoverTimer.stop(); }
            else { hover[0] += hover[0] < target ? step : -step; }
            card.repaint();
        });
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { if (!hoverTimer.isRunning()) hoverTimer.start(); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { if (!hoverTimer.isRunning()) hoverTimer.start(); }
        });
        card.setLayout(new BorderLayout(12, 8));
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));

        JPanel title = new JPanel(new BorderLayout(8, 0));
        title.setOpaque(false);
        JLabel code = new JLabel(request.getRequestCode() + "  ·  "
                + request.getBorrowerName());
        code.setFont(AppTheme.semibold(13));
        code.setForeground(AppTheme.foreground());
        title.add(code, BorderLayout.CENTER);
        title.add(UiSupport.statusBadges(request, scopeRole, scopeRole == null),
                BorderLayout.EAST);

        String equipment = equipmentSummary(request);
        int schedules = request.getOccurrences().size();
        JLabel meta = new JLabel(request.getVenue() + "  ·  "
                + request.getEventDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                + "  ·  " + UiSupport.timeRange(request.getStartTime(), request.getEndTime())
                + (schedules > 1 ? "  ·  " + schedules + " schedules" : ""));
        meta.setFont(AppTheme.regular(10));
        meta.setForeground(AppTheme.muted());
        JLabel itemLine = new JLabel(equipment);
        itemLine.setFont(AppTheme.regular(10));
        itemLine.setForeground(AppTheme.muted());
        details.add(title);
        details.add(Box.createVerticalStrut(5));
        details.add(meta);
        details.add(Box.createVerticalStrut(4));
        details.add(itemLine);
        if (mode == Mode.TRANSACTION_HISTORY) {
            JLabel approval = new JLabel(
                    "Approval progress: " + RequestStatus.approvalProgress(request, scopeRole));
            approval.setFont(AppTheme.regular(10));
            approval.setForeground(AppTheme.muted());
            details.add(Box.createVerticalStrut(4));
            details.add(approval);
        }
        String remarks = departmentRemarks(request);
        if (!remarks.isEmpty()) {
            JLabel remarksLine = new JLabel("<html>" + remarks + "</html>");
            remarksLine.setFont(AppTheme.regular(10));
            remarksLine.setForeground(AppTheme.muted());
            details.add(Box.createVerticalStrut(4));
            details.add(remarksLine);
        }
        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        PrimaryButton view = new PrimaryButton("View details", PrimaryButton.Style.SECONDARY);
        view.addActionListener(event -> new RequestDetailsDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), request, scopeRole,
                actor, requestController)
                .setVisible(true));
        actions.add(view);
        addModeActions(actions, request);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void addModeActions(JPanel actions, BorrowRequest request) {
        if (mode == Mode.STAFF_QUEUE) {
            if (hasOwnGroup(request, "PENDING")) {
                PrimaryButton approve = new PrimaryButton("Accept");
                approve.setToolTipText("Accept and assign your department's equipment");
                approve.addActionListener(event -> assignRequest(request, approve));
                PrimaryButton decline = new PrimaryButton("Decline", PrimaryButton.Style.DANGER);
                decline.setToolTipText("Decline only your department's equipment");
                decline.addActionListener(event -> declineRequest(request, decline));
                // Requirement III.3: remove "Request Changes" from ICT System Admin Request section
                actions.add(approve);
                actions.add(decline);
            }
        } else if (mode == Mode.ADMIN_REVIEW || mode == Mode.DIRECTOR_REVIEW) {
            PrimaryButton approve = new PrimaryButton("Approve");
            approve.addActionListener(event -> reviewRequest(request, true, approve));
            PrimaryButton reject = new PrimaryButton("Decline", PrimaryButton.Style.DANGER);
            reject.addActionListener(event -> reviewRequest(request, false, reject));
            actions.add(approve);
            actions.add(reject);
        } else if (mode == Mode.SCHEDULE_ASSIGNMENT || mode == Mode.TRANSACTION_HISTORY) {
            boolean finalApproved = RequestStatus.isFinalApprovalGranted(request);
            if (mode == Mode.SCHEDULE_ASSIGNMENT && finalApproved
                    && hasOwnGroup(request, "ASSIGNED")
                    && hasProcessableGroup(request, "ASSIGNED", false)) {
                PrimaryButton release = new PrimaryButton(
                        RequestStatus.departmentLabel(actor.getRole()) + " release");
                release.addActionListener(event -> updateTransaction(request, true, release));
                actions.add(release);
            }
            if (canInspectReturn(request)) {
                PrimaryButton returned = new PrimaryButton(
                        "Inspect return", PrimaryButton.Style.SECONDARY);
                returned.setToolTipText(
                        "Inspect the returned equipment after the borrower confirms receipt");
                returned.addActionListener(event -> updateTransaction(request, false, returned));
                actions.add(returned);
            }
        }
        // Requirement III.3: keep Delete only in Transaction History (and allowed contexts)
        if (isIctAdmin() && !"CANCELLED".equals(request.getStatus())
                && mode == Mode.TRANSACTION_HISTORY) {
            PrimaryButton delete = new PrimaryButton("Delete", PrimaryButton.Style.DANGER);
            delete.setToolTipText("Delete this request (ICT System Admin only)");
            delete.addActionListener(event -> deleteAsAdmin(request, delete));
            actions.add(delete);
        }
    }

    private boolean hasOwnGroup(BorrowRequest request, String status) {
        for (DepartmentStatus department : request.getDepartments()) {
            if (actor.getRole().equalsIgnoreCase(department.getCustodianRole())
                    && status.equals(department.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /** Staff may inspect only once the borrower has confirmed receipt (RECEIVED). */
    private boolean canInspectReturn(BorrowRequest request) {
        return isStaffActor(actor)
                && hasOwnGroup(request, "RECEIVED")
                && hasProcessableGroup(request, "RECEIVED", true);
    }

    /** True when one of this department's groups has reached its scheduled date. */
    private boolean hasProcessableGroup(BorrowRequest request, String status, boolean useEnd) {
        if (scopeRole == null) {
            return false;
        }
        for (DepartmentStatus department : request.getDepartments()) {
            if (!scopeRole.equalsIgnoreCase(department.getCustodianRole())
                    || !status.equals(department.getStatus())) {
                continue;
            }
            java.time.LocalDateTime boundary = useEnd
                    ? department.getScheduleEnd() : department.getScheduleStart();
            if (boundary == null
                    || !boundary.toLocalDate().isAfter(src.ui.components.DateFilter.today())) {
                return true;
            }
        }
        return false;
    }

    private boolean isIctAdmin() {
        return actor != null && "ICT_STAFF".equalsIgnoreCase(actor.getRole());
    }

    private void deleteAsAdmin(BorrowRequest request, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        long requestId = request.getRequestId();
        String label = requestLabel(request);
        int answer = UiSupport.showConfirmDialog(this,
                "Delete request " + label + "?\n"
                        + "It will be removed from all request lists and kept for audit. "
                        + "You can undo for " + UndoPolicy.RECOVERY_LABEL + ".",
                "Delete request", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            UiSupport.endAction(source);
            return;
        }
        String reason = UiSupport.showInputDialog(this,
                "Reason for deleting request " + label + ":",
                "Delete request", JOptionPane.QUESTION_MESSAGE);
        if (reason == null || reason.isBlank()) {
            UiSupport.endAction(source);
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                requestController.deleteRequestAsAdmin(actor, requestId, reason);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadRequests();
                    offerAdminUndo(label, requestId);
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(RequestQueuePanel.this,
                            messageOf(exception), "Unable to delete request",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }

    private void offerAdminUndo(String label, long requestId) {
        int choice = UiSupport.showUndoDialog(this,
                label + " was deleted.\nUndo is available for " + UndoPolicy.RECOVERY_LABEL + ".",
                "Request deleted");
        if (choice != 0) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                requestController.restoreRequestAsAdmin(actor, requestId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadRequests();
                    UiSupport.showMessageDialog(RequestQueuePanel.this,
                            label + " was restored with its original status.",
                            "Request restored", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(RequestQueuePanel.this,
                            messageOf(exception), "Unable to restore request",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String requestLabel(BorrowRequest request) {
        return request.getRequestCode() == null
                ? "#" + request.getRequestId() : request.getRequestCode();
    }

    /** Department remarks (e.g. decline reasons) visible to this view. */
    private String departmentRemarks(BorrowRequest request) {
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

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void declineRequest(BorrowRequest request, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        String remarks = UiSupport.showInputDialog(
                this, "Reason for declining your department's equipment:",
                "Decline " + RequestStatus.departmentLabel(actor.getRole()) + " equipment",
                JOptionPane.WARNING_MESSAGE);
        if (remarks == null) {
            UiSupport.endAction(source);
            return;
        }
        if (remarks.isBlank()) {
            UiSupport.endAction(source);
            UiSupport.showMessageDialog(
                    this, "Enter a reason for declining.",
                    "Reason required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAction("Unable to decline request", () ->
                requestController.declineStaffRequest(actor, request.getRequestId(), remarks),
                source);
    }

    private void assignRequest(BorrowRequest request, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        List<src.model.ItemInspection> assignments = ItemInspectionDialog.show(
                this, request, actor.getRole(), ItemInspectionDialog.Kind.ASSIGN);
        if (assignments == null) {
            UiSupport.endAction(source);
            return;
        }
        runAction("Unable to assign equipment", () ->
                requestController.assignStaffRequest(
                        actor, request.getRequestId(), assignments), source);
    }

    private void requestChanges(BorrowRequest request, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        String remarks = UiSupport.showInputDialog(
                this, "Explain what the borrower needs to change:",
                "Request changes", JOptionPane.QUESTION_MESSAGE);
        if (remarks == null) {
            UiSupport.endAction(source);
            return;
        }
        if (remarks.isBlank()) {
            UiSupport.endAction(source);
            UiSupport.showMessageDialog(
                    this, "Enter a reason for the requested changes.",
                    "Reason required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAction("Unable to update request", () ->
                requestController.markStaffRequestNeedsAction(
                        actor, request.getRequestId(), remarks), source);
    }

    private void reviewRequest(
            BorrowRequest request, boolean approve, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        String prompt = approve
                ? "Optional remarks:"
                : "Reason for declining this request:";
        String remarks = UiSupport.showInputDialog(
                this, prompt, approve ? "Approve request" : "Decline request",
                JOptionPane.QUESTION_MESSAGE);
        if (remarks == null) {
            UiSupport.endAction(source);
            return;
        }
        if (!approve && remarks.isBlank()) {
            UiSupport.endAction(source);
            UiSupport.showMessageDialog(
                    this, "Enter a reason before declining.",
                    "Reason required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAction("Unable to review request", () -> {
            if (mode == Mode.ADMIN_REVIEW) {
                requestController.reviewByAdmin(
                        actor, request.getRequestId(), approve, remarks);
            } else {
                requestController.reviewByDirector(
                        actor, request.getRequestId(), approve, remarks);
            }
        }, source);
    }

    private void updateTransaction(
            BorrowRequest request, boolean release, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        List<src.model.ItemInspection> inspections = ItemInspectionDialog.show(
                this, request, actor.getRole(),
                release ? ItemInspectionDialog.Kind.RELEASE : ItemInspectionDialog.Kind.RETURN);
        if (inspections == null) {
            UiSupport.endAction(source);
            return;
        }
        runAction(
                release ? "Unable to release equipment" : "Unable to record return",
                () -> {
                    if (release) {
                        requestController.releaseStaffAssignment(
                                actor, request.getRequestId(), inspections);
                    } else {
                        requestController.recordStaffReturn(
                                actor, request.getRequestId(), inspections);
                    }
                }, source);
    }

    private boolean isActorItem(EquipmentRequestItem item, String groupStatus) {
        return actor.getRole().equalsIgnoreCase(item.getCustodianRole())
                && groupStatus.equals(item.getGroupStatus());
    }

    private String equipmentSummary(BorrowRequest request) {
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

    private void runAction(
            String errorTitle, ThrowingAction action, javax.swing.AbstractButton source) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadRequests();
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(
                            RequestQueuePanel.this, messageOf(exception),
                            errorTitle, JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }

    private JPanel message(String text, boolean error) {
        SurfacePanel panel = new SurfacePanel();
        panel.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(11));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private void showMessage(String text, boolean error) {
        requestList.removeAll();
        requestList.add(message(text, error));
        requestList.revalidate();
        requestList.repaint();
    }

    private static RequestStatus.Category[] statusCategoriesFor(Mode mode) {
        return switch (mode) {
            case STAFF_QUEUE -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.PENDING,
                    RequestStatus.Category.APPROVED, RequestStatus.Category.PARTIALLY_RELEASED,
                    RequestStatus.Category.COMPLETED, RequestStatus.Category.OVERDUE,
                    RequestStatus.Category.NEEDS_ACTION, RequestStatus.Category.DECLINED,
                    RequestStatus.Category.CANCELLED
            };
            case SCHEDULE_ASSIGNMENT -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.PENDING,
                    RequestStatus.Category.APPROVED, RequestStatus.Category.PARTIALLY_RELEASED,
                    RequestStatus.Category.OVERDUE, RequestStatus.Category.NEEDS_ACTION,
                    RequestStatus.Category.DECLINED, RequestStatus.Category.CANCELLED
            };
            case TRANSACTION_HISTORY -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.PENDING,
                    RequestStatus.Category.APPROVED, RequestStatus.Category.PARTIALLY_APPROVED,
                    RequestStatus.Category.FULLY_APPROVED,
                    RequestStatus.Category.PARTIALLY_RELEASED,
                    RequestStatus.Category.FULLY_RELEASED,
                    RequestStatus.Category.COMPLETED, RequestStatus.Category.DECLINED,
                    RequestStatus.Category.OVERDUE, RequestStatus.Category.NEEDS_ACTION,
                    RequestStatus.Category.ADMIN_REVIEW, RequestStatus.Category.DIRECTOR_REVIEW,
                    RequestStatus.Category.FINAL_APPROVED, RequestStatus.Category.CANCELLED
            };
            case ADMIN_REVIEW -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.ADMIN_REVIEW,
                    RequestStatus.Category.PENDING, RequestStatus.Category.APPROVED,
                    RequestStatus.Category.DECLINED, RequestStatus.Category.NEEDS_ACTION,
                    RequestStatus.Category.OVERDUE, RequestStatus.Category.CANCELLED
            };
            case DIRECTOR_REVIEW -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.DIRECTOR_REVIEW,
                    RequestStatus.Category.FINAL_APPROVED, RequestStatus.Category.PENDING,
                    RequestStatus.Category.DECLINED, RequestStatus.Category.OVERDUE,
                    RequestStatus.Category.CANCELLED
            };
            case ALL_REQUESTS -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.PENDING,
                    RequestStatus.Category.APPROVED, RequestStatus.Category.PARTIALLY_RELEASED,
                    RequestStatus.Category.COMPLETED, RequestStatus.Category.DECLINED,
                    RequestStatus.Category.OVERDUE, RequestStatus.Category.NEEDS_ACTION,
                    RequestStatus.Category.CANCELLED, RequestStatus.Category.ADMIN_REVIEW,
                    RequestStatus.Category.DIRECTOR_REVIEW, RequestStatus.Category.FINAL_APPROVED
            };
        };
    }

    private static boolean statusCategoriesContains(Mode mode, RequestStatus.Category category) {
        for (RequestStatus.Category candidate : statusCategoriesFor(mode)) {
            if (candidate == category) return true;
        }
        return false;
    }

    private String titleForMode() {
        switch (mode) {
            case STAFF_QUEUE:
                return "Requests";
            case ADMIN_REVIEW:
                return "Borrowing requests";
            case DIRECTOR_REVIEW:
                return "Request approval";
            case SCHEDULE_ASSIGNMENT:
                return "Schedule & assignment";
            case TRANSACTION_HISTORY:
                return "Transaction history";
            case ALL_REQUESTS:
            default:
                return "All requests";
        }
    }

    private String subtitleForMode() {
        switch (mode) {
            case STAFF_QUEUE:
                return "Requests with your department's equipment. Approve or decline independently of other departments.";
            case ADMIN_REVIEW:
                return "Review staff-assigned requests before they go to the campus director.";
            case DIRECTOR_REVIEW:
                return "Final approval for requests ready for campus director review.";
            case SCHEDULE_ASSIGNMENT:
                return "Release your department's approved equipment and inspect returns, independently of other departments.";
            case TRANSACTION_HISTORY:
                return "Pending, approved, declined, overdue, and completed request records.";
            case ALL_REQUESTS:
            default:
                return scopeRole == null
                        ? "Monitor every request and each department's decision."
                        : "Every request containing your department's equipment.";
        }
    }

    private String emptyMessage() {
        switch (mode) {
            case STAFF_QUEUE:
                return "No requests are waiting for your equipment group.";
            case ADMIN_REVIEW:
            case DIRECTOR_REVIEW:
                return "There are no requests waiting for approval.";
            case SCHEDULE_ASSIGNMENT:
                return "Nothing to release or receive. Requests appear here after final approval.";
            case TRANSACTION_HISTORY:
                return "No transaction records match the current filters.";
            case ALL_REQUESTS:
            default:
                return "No request records have been submitted yet.";
        }
    }

    private String messageOf(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause() : exception;
        return cause.getMessage();
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
