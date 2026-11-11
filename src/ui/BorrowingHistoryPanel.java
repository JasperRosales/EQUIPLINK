package src.ui;

import src.model.BorrowRequest;
import src.model.EquipmentRequestItem;
import src.controller.BorrowRequestController;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BorrowingHistoryPanel extends JPanel {

    private final long borrowerId;
    private final BorrowRequestController requestController;
    private final JPanel historyList = new JPanel();

    private final JTextField search = new JTextField(18);
    private final JComboBox<String> statusFilter = new JComboBox<>(
            new String[] { "All history", "Completed", "Rejected", "Cancelled" });
    private final src.ui.components.DateFilter dateFilter =
            new src.ui.components.DateFilter();
    private List<BorrowRequest> loaded = List.of();

    public BorrowingHistoryPanel(long borrowerId, BorrowRequestController requestController) {
        this.borrowerId = borrowerId;
        this.requestController = requestController;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));

        JLabel heading = new JLabel("Borrowing history");
        heading.setFont(AppTheme.semibold(18));
        heading.setForeground(AppTheme.foreground());
        JLabel hint = new JLabel("Completed, cancelled, and declined equipment requests.");
        hint.setFont(AppTheme.regular(11));
        hint.setForeground(AppTheme.muted());
        JPanel titleBar = new JPanel(new BorderLayout(8, 8));
        titleBar.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(heading);
        titles.add(Box.createVerticalStrut(4));
        titles.add(hint);
        titleBar.add(titles, BorderLayout.NORTH);

        JPanel filters = new JPanel(new BorderLayout(8, 0));
        filters.setOpaque(false);
        UiSupport.styleInput(search);
        search.putClientProperty("JTextField.placeholderText",
                "Search by code, venue, purpose, or equipment");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(UiSupport.documentListener(() -> showHistory(loaded)));
        filters.add(search, BorderLayout.CENTER);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        statusFilter.setFont(AppTheme.regular(12));
        statusFilter.setPreferredSize(new Dimension(150, 40));
        statusFilter.addActionListener(event -> showHistory(loaded));
        dateFilter.addChangeListener(() -> showHistory(loaded));
        tools.add(statusFilter);
        tools.add(dateFilter);
        filters.add(tools, BorderLayout.EAST);
        titleBar.add(filters, BorderLayout.CENTER);
        add(titleBar);
        add(Box.createVerticalStrut(14));

        historyList.setOpaque(false);
        historyList.setLayout(new BoxLayout(historyList, BoxLayout.Y_AXIS));
        add(historyList);
        loadHistory();
    }

    private void loadHistory() {
        historyList.removeAll();
        historyList.add(message("Loading borrowing history…", false));
        new SwingWorker<List<BorrowRequest>, Void>() {
            @Override
            protected List<BorrowRequest> doInBackground() throws Exception {
                return requestController.findBorrowingHistory(borrowerId);
            }

            @Override
            protected void done() {
                try {
                    showHistory(get());
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    historyList.removeAll();
                    historyList.add(message(
                            "Unable to load borrowing history: " + cause.getMessage(), true));
                    historyList.revalidate();
                }
            }
        }.execute();
    }

    private void showHistory(List<BorrowRequest> all) {
        loaded = all;
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String status = String.valueOf(statusFilter.getSelectedItem());
        List<BorrowRequest> requests = new ArrayList<>();
        for (BorrowRequest request : all) {
            if (!dateFilter.matches(request.getEventDate())) {
                continue;
            }
            if ("Completed".equals(status) && !"COMPLETED".equals(request.getStatus())) continue;
            if ("Rejected".equals(status) && !"REJECTED".equals(request.getStatus())) continue;
            if ("Cancelled".equals(status) && !"CANCELLED".equals(request.getStatus())) continue;
            if (!query.isEmpty() && !matchesQuery(request, query)) {
                continue;
            }
            requests.add(request);
        }
        historyList.removeAll();
        if (requests.isEmpty()) {
            historyList.add(message(all.isEmpty()
                    ? "Past borrowing records will appear here after a request is completed or closed."
                    : "No records match the current search or filters.",
                    false));
        } else {
            for (BorrowRequest request : requests) {
                historyList.add(historyCard(request));
                historyList.add(Box.createVerticalStrut(10));
            }
        }
        historyList.revalidate();
        historyList.repaint();
    }

    private boolean matchesQuery(BorrowRequest request, String query) {
        StringBuilder text = new StringBuilder()
                .append(request.getRequestCode()).append(' ')
                .append(request.getVenue()).append(' ')
                .append(request.getPurpose()).append(' ')
                .append(request.getStatus()).append(' ')
                .append(request.getRemarks() == null ? "" : request.getRemarks());
        for (EquipmentRequestItem item : request.getEquipmentItems()) {
            text.append(' ').append(item.getEquipmentName())
                    .append(' ').append(item.getEquipmentCode())
                    .append(' ').append(item.getCategory());
        }
        return text.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private JPanel historyCard(BorrowRequest request) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout(12, 8));
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setOpaque(false);
        JLabel code = new JLabel(request.getRequestCode());
        code.setFont(AppTheme.semibold(13));
        code.setForeground(AppTheme.foreground());
        JLabel status = new src.ui.components.StatusBadge(request.getStatus());
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        titleRow.add(code, BorderLayout.WEST);
        titleRow.add(status, BorderLayout.EAST);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
        JLabel subtitle = new JLabel(request.getVenue() + "  ·  "
                + request.getEventDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                + "  ·  " + request.getStartTime().format(timeFmt)
                + "–" + request.getEndTime().format(timeFmt)
                + "  ·  " + request.getEquipmentItems().size() + " item(s)");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        details.add(titleRow);
        details.add(Box.createVerticalStrut(6));
        details.add(subtitle);
        card.add(details, BorderLayout.CENTER);

        PrimaryButton view = new PrimaryButton("View details", PrimaryButton.Style.SECONDARY);
        view.addActionListener(event -> new RequestDetailsDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), request).setVisible(true));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(view);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel message(String text, boolean error) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(12));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        card.add(label, BorderLayout.CENTER);
        return card;
    }
}
