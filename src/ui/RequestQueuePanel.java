package src.ui;

import src.controller.BorrowRequestController;
import src.model.BorrowRequest;
import src.model.EquipmentRequestItem;
import src.model.User;
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
import java.util.List;
import java.util.concurrent.ExecutionException;

public class RequestQueuePanel extends JPanel {

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
    private final JPanel requestList = new JPanel();
    private List<BorrowRequest> requests = List.of();

    public RequestQueuePanel(
            User actor, BorrowRequestController requestController, Mode mode) {
        this.actor = actor;
        this.requestController = requestController;
        this.mode = mode;
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
        tools.add(dateSort);
        PrimaryButton refresh = new PrimaryButton("Refresh", PrimaryButton.Style.SECONDARY);
        refresh.addActionListener(event -> loadRequests());
        tools.add(refresh);
        filters.add(tools, BorderLayout.EAST);
        header.add(filters, BorderLayout.CENTER);
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(14));
    }

    private void loadRequests() {
        showMessage("Loading requests…", false);
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
                        return requestController.findHistoryForRole(actor);
                    case ALL_REQUESTS:
                    default:
                        return requestController.findAllRequests(actor);
                }
            }

            @Override
            protected void done() {
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
        String query = search.getText().trim().toLowerCase();
        List<BorrowRequest> filtered = new ArrayList<>();
        for (BorrowRequest request : requests) {
            if (searchableText(request).contains(query)) {
                filtered.add(request);
            }
        }
        Comparator<BorrowRequest> byDate = Comparator.comparing(BorrowRequest::getSubmittedAt);
        if (dateSort.getSelectedIndex() == 0) {
            byDate = byDate.reversed();
        }
        filtered.sort(byDate);

        if (filtered.isEmpty()) {
            requestList.add(message(
                    requests.isEmpty()
                            ? emptyMessage()
                            : "No requests match the current search.",
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

    private String searchableText(BorrowRequest request) {
        StringBuilder text = new StringBuilder()
                .append(request.getRequestCode()).append(' ')
                .append(request.getBorrowerName()).append(' ')
                .append(request.getVenue()).append(' ')
                .append(request.getPurpose()).append(' ')
                .append(request.getStatus());
        for (EquipmentRequestItem item : request.getEquipmentItems()) {
            text.append(' ').append(item.getEquipmentName())
                    .append(' ').append(item.getEquipmentCode());
        }
        return text.toString().toLowerCase();
    }

    private JPanel requestCard(BorrowRequest request) {
        SurfacePanel card = new SurfacePanel();
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
        JLabel status = new src.ui.components.StatusBadge(request.getStatus());
        title.add(code, BorderLayout.CENTER);
        title.add(status, BorderLayout.EAST);

        String equipment = equipmentSummary(request);
        JLabel meta = new JLabel(request.getVenue() + "  ·  "
                + request.getEventDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                + "  ·  " + request.getStartTime() + "–" + request.getEndTime());
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
        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        PrimaryButton view = new PrimaryButton("View details", PrimaryButton.Style.SECONDARY);
        view.addActionListener(event -> new RequestDetailsDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), request).setVisible(true));
        actions.add(view);
        addModeActions(actions, request);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void addModeActions(JPanel actions, BorrowRequest request) {
        if (mode == Mode.STAFF_QUEUE) {
            PrimaryButton assign = new PrimaryButton("Assign equipment");
            assign.addActionListener(event -> assignRequest(request));
            PrimaryButton needsAction = new PrimaryButton(
                    "Request changes", PrimaryButton.Style.SECONDARY);
            needsAction.addActionListener(event -> requestChanges(request));
            actions.add(assign);
            actions.add(needsAction);
        } else if (mode == Mode.ADMIN_REVIEW || mode == Mode.DIRECTOR_REVIEW) {
            PrimaryButton approve = new PrimaryButton("Approve");
            approve.addActionListener(event -> reviewRequest(request, true));
            PrimaryButton reject = new PrimaryButton("Reject", PrimaryButton.Style.DANGER);
            reject.addActionListener(event -> reviewRequest(request, false));
            actions.add(approve);
            actions.add(reject);
        } else if (mode == Mode.SCHEDULE_ASSIGNMENT) {
            boolean hasAssigned = request.getEquipmentItems().stream()
                    .anyMatch(item -> isActorItem(item, "ASSIGNED"));
            boolean hasReleased = request.getEquipmentItems().stream()
                    .anyMatch(item -> isActorItem(item, "RELEASED"));
            if (hasAssigned) {
                PrimaryButton release = new PrimaryButton("Release equipment");
                release.addActionListener(event -> updateTransaction(request, true));
                actions.add(release);
            } else if (hasReleased) {
                PrimaryButton returned = new PrimaryButton(
                        "Inspect return", PrimaryButton.Style.SECONDARY);
                returned.addActionListener(event -> updateTransaction(request, false));
                actions.add(returned);
            }
        }
    }

    private void assignRequest(BorrowRequest request) {
        List<src.model.ItemInspection> assignments = ItemInspectionDialog.show(
                this, request, actor.getRole(), ItemInspectionDialog.Kind.ASSIGN);
        if (assignments == null) {
            return;
        }
        runAction("Unable to assign equipment", () ->
                requestController.assignStaffRequest(
                        actor, request.getRequestId(), assignments));
    }

    private void requestChanges(BorrowRequest request) {
        String remarks = JOptionPane.showInputDialog(
                this, "Explain what the borrower needs to change:",
                "Request changes", JOptionPane.QUESTION_MESSAGE);
        if (remarks == null) {
            return;
        }
        if (remarks.isBlank()) {
            JOptionPane.showMessageDialog(
                    this, "Enter a reason for the requested changes.",
                    "Reason required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAction("Unable to update request", () ->
                requestController.markStaffRequestNeedsAction(
                        actor, request.getRequestId(), remarks));
    }

    private void reviewRequest(BorrowRequest request, boolean approve) {
        String prompt = approve
                ? "Optional remarks:"
                : "Reason for rejecting this request:";
        String remarks = JOptionPane.showInputDialog(
                this, prompt, approve ? "Approve request" : "Reject request",
                JOptionPane.QUESTION_MESSAGE);
        if (remarks == null) {
            return;
        }
        if (!approve && remarks.isBlank()) {
            JOptionPane.showMessageDialog(
                    this, "Enter a reason before rejecting.",
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
        });
    }

    private void updateTransaction(BorrowRequest request, boolean release) {
        List<src.model.ItemInspection> inspections = ItemInspectionDialog.show(
                this, request, actor.getRole(),
                release ? ItemInspectionDialog.Kind.RELEASE : ItemInspectionDialog.Kind.RETURN);
        if (inspections == null) {
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
                });
    }

    private boolean isActorItem(EquipmentRequestItem item, String groupStatus) {
        return actor.getRole().equalsIgnoreCase(item.getCustodianRole())
                && groupStatus.equals(item.getGroupStatus());
    }

    private String equipmentSummary(BorrowRequest request) {
        StringBuilder summary = new StringBuilder();
        for (EquipmentRequestItem item : request.getEquipmentItems()) {
            if (mode == Mode.STAFF_QUEUE || mode == Mode.SCHEDULE_ASSIGNMENT
                    || mode == Mode.TRANSACTION_HISTORY) {
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

    private void runAction(String errorTitle, ThrowingAction action) {
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
                    JOptionPane.showMessageDialog(
                            RequestQueuePanel.this, messageOf(exception),
                            errorTitle, JOptionPane.ERROR_MESSAGE);
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
                return "New requests for your equipment group. Assign available quantities or request changes.";
            case ADMIN_REVIEW:
                return "Review staff-assigned requests before they go to the campus director.";
            case DIRECTOR_REVIEW:
                return "Final approval for requests ready for campus director review.";
            case SCHEDULE_ASSIGNMENT:
                return "Release approved equipment and inspect returns. Finished items move to Transaction history.";
            case TRANSACTION_HISTORY:
                return "Returned equipment and closed requests for your equipment group.";
            case ALL_REQUESTS:
            default:
                return "Search and review request records across all statuses.";
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
                return "Completed transactions will appear here.";
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
