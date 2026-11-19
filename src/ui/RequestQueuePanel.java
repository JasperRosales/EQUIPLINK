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

public class RequestQueuePanel extends RequestQueuePanelPart4 implements Refreshable {

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

    void addHeader() {
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

    void styleCombo(javax.swing.JComboBox<?> combo, int width, String tooltip) {
        combo.setFont(AppTheme.regular(12));
        combo.setPreferredSize(new java.awt.Dimension(width, 40));
        combo.setToolTipText(tooltip);
        combo.addActionListener(event -> showRequests());
    }

    @Override
    public void reloadData() {
        loadRequests(true);
    }

    void requestChanges(BorrowRequest request, javax.swing.AbstractButton source) {
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
}
