package src.ui;

import src.model.BorrowRequest;
import src.controller.BorrowRequestController;
import src.util.UndoPolicy;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class MyRequestsPanel extends MyRequestsPanelPart1 implements Refreshable {

    public MyRequestsPanel(
            long borrowerId,
            BorrowRequestController requestController,
            Consumer<BorrowRequest> editHandler,
            Runnable changedHandler) {
        this(borrowerId, requestController, editHandler, changedHandler, "ALL");
    }

    public MyRequestsPanel(
            long borrowerId,
            BorrowRequestController requestController,
            Consumer<BorrowRequest> editHandler,
            Runnable changedHandler,
            String statusFilter) {
        this.borrowerId = borrowerId;
        this.requestController = requestController;
        this.editHandler = editHandler;
        this.changedHandler = changedHandler;
        this.statusFilter = statusFilter == null ? "ALL" : statusFilter;
        List<String> options = new ArrayList<>(List.of(
                ALL_REQUESTS,
                ALL_ACTIVE,
                src.model.RequestStatus.Category.PENDING.getLabel(),
                src.model.RequestStatus.Category.APPROVED.getLabel(),
                RELEASED,
                src.model.RequestStatus.Category.COMPLETED.getLabel(),
                src.model.RequestStatus.Category.DECLINED.getLabel(),
                src.model.RequestStatus.Category.CANCELLED.getLabel(),
                src.model.RequestStatus.Category.NEEDS_ACTION.getLabel(),
                src.model.RequestStatus.Category.OVERDUE.getLabel()));
        categoryFilter = new JComboBox<>(options.toArray(new String[0]));
        if ("PENDING".equals(this.statusFilter)) {
            categoryFilter.setSelectedItem(src.model.RequestStatus.Category.PENDING.getLabel());
        } else if ("BORROWED".equals(this.statusFilter)) {
            categoryFilter.setSelectedItem(RELEASED);
        } else if (!"ALL".equals(this.statusFilter)) {
            src.model.RequestStatus.Category initial =
                    src.model.RequestStatus.Category.fromLabel(this.statusFilter);
            String label = switch (initial) {
                case ADMIN_REVIEW, DIRECTOR_REVIEW, FINAL_APPROVED ->
                        src.model.RequestStatus.Category.APPROVED.getLabel();
                case PARTIALLY_RELEASED, FULLY_RELEASED -> RELEASED;
                case PARTIALLY_APPROVED, FULLY_APPROVED ->
                        src.model.RequestStatus.Category.APPROVED.getLabel();
                default -> initial.getLabel();
            };
            categoryFilter.setSelectedItem(options.contains(label) ? label : ALL_REQUESTS);
        }

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        JLabel heading = new JLabel("Your requests");
        heading.setFont(AppTheme.semibold(18));
        heading.setForeground(AppTheme.foreground());
        add(heading);
        add(Box.createVerticalStrut(12));

        JPanel filters = new JPanel(new BorderLayout(8, 0));
        filters.setOpaque(false);
        UiSupport.styleInput(search);
        search.putClientProperty("JTextField.placeholderText", "Search by code, venue, or equipment");
        search.getDocument().addDocumentListener(UiSupport.documentListener(this::showRequests));
        filters.add(search, BorderLayout.CENTER);
        dateSort.setFont(AppTheme.regular(12));
        dateSort.setPreferredSize(new java.awt.Dimension(150, 40));
        dateSort.addActionListener(event -> showRequests());
        dateFilter.addChangeListener(this::showRequests);
        categoryFilter.setFont(AppTheme.regular(12));
        categoryFilter.setPreferredSize(new java.awt.Dimension(200, 40));
        categoryFilter.setToolTipText("Filter by the latest request status");
        categoryFilter.addActionListener(event -> showRequests());
        JPanel tools = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        tools.add(categoryFilter);
        tools.add(dateFilter);
        tools.add(dateSort);
        PrimaryButton refresh = new PrimaryButton("Refresh", PrimaryButton.Style.SECONDARY);
        refresh.setToolTipText("Reload requests and return the calendar to today");
        refresh.addActionListener(event -> {
            dateFilter.resetToToday();
            reload();
        });
        tools.add(refresh);
        filters.add(tools, BorderLayout.EAST);
        add(filters);
        add(Box.createVerticalStrut(8));
        resultCount.setFont(AppTheme.semibold(11));
        resultCount.setForeground(AppTheme.primary());
        add(resultCount);
        add(Box.createVerticalStrut(8));

        requestCards.setOpaque(false);
        requestCards.setLayout(new BoxLayout(requestCards, BoxLayout.Y_AXIS));
        add(requestCards);
        reload();
    }

    @Override
    public void reloadData() {
        reload(true);
    }

    public void reload() {
        reload(false);
    }

    void reload(boolean quiet) {
        if (loading) {
            return;
        }
        loading = true;
        if (!quiet) {
            requestCards.removeAll();
            requestCards.add(messageCard("Loading your requests…", false));
            requestCards.revalidate();
            requestCards.repaint();
        }

        new SwingWorker<List<BorrowRequest>, Void>() {
            @Override
            protected List<BorrowRequest> doInBackground() throws Exception {
                // Every request the borrower owns, so any status category can be filtered.
                return requestController.findAllForBorrower(borrowerId);
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    requests = get();
                    showRequests();
                } catch (Exception exception) {
                    String message = exception.getCause() == null
                            ? exception.getMessage()
                            : exception.getCause().getMessage();
                    showError("Unable to load your requests: " + message);
                }
            }
        }.execute();
    }

    void showRequests() {
        requestCards.removeAll();
        dateFilter.setRequestDays(buildRequestDates());
        String query = search.getText().trim().toLowerCase();
        List<BorrowRequest> filtered = new ArrayList<>();
        for (BorrowRequest request : requests) {
            if (matchesFilter(request) && matchesDate(dateFilter, request)
                    && searchableText(request).contains(query)) {
                filtered.add(request);
            }
        }
        Comparator<BorrowRequest> byDate = Comparator.comparing(BorrowRequest::getSubmittedAt);
        if (dateSort.getSelectedIndex() == 0) {
            byDate = byDate.reversed();
        }
        filtered.sort(byDate);
        resultCount.setText(filtered.size() + " request(s) shown");
        if (filtered.isEmpty()) {
            requestCards.add(messageCard(
                    requests.isEmpty()
                            ? "No requests yet. Start by requesting equipment."
                            : "No requests match the current search or filter.",
                    false));
        } else {
            for (BorrowRequest request : filtered) {
                requestCards.add(requestCard(request));
                requestCards.add(Box.createVerticalStrut(10));
            }
        }
        requestCards.revalidate();
        requestCards.repaint();
    }

    void showError(String message) {
        requestCards.removeAll();
        requestCards.add(messageCard(message, true));
        requestCards.revalidate();
        requestCards.repaint();
    }
}
