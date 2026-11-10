package src.ui;

import src.model.BorrowRequest;
import src.controller.BorrowRequestController;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BorrowingHistoryPanel extends JPanel {

    private final long borrowerId;
    private final BorrowRequestController requestController;
    private final JPanel historyList = new JPanel();

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
        add(heading);
        add(Box.createVerticalStrut(4));
        add(hint);
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

    private void showHistory(List<BorrowRequest> requests) {
        historyList.removeAll();
        if (requests.isEmpty()) {
            historyList.add(message(
                    "Past borrowing records will appear here after a request is completed or closed.",
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
        JLabel subtitle = new JLabel(request.getVenue() + "  ·  "
                + request.getEventDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
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
