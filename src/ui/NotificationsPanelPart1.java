package src.ui;

import src.controller.NotificationController;
import src.model.Notification;
import src.ui.components.DateFilter;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;
import src.util.UndoPolicy;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Part 1 of NotificationsPanel, split out to keep files short. */
public abstract class NotificationsPanelPart1 extends JPanel {

    long userId;
    src.model.User user;
    NotificationController notificationController;
    src.controller.BorrowRequestController requestController;
    Runnable onChanged;
    final PrimaryButton deleteAll = new PrimaryButton(
            "Delete all", PrimaryButton.Style.DANGER);
    final JPanel notificationList = new JPanel();
    final PrimaryButton markAllRead = new PrimaryButton(
            "Mark all read", PrimaryButton.Style.SECONDARY);
    final JTextField search = new JTextField(18);
    final JComboBox<String> kindFilter = new JComboBox<>(
            new String[] { "All types", "Request", "Borrowing & Returns", "Account" });
    final JComboBox<String> readFilter = new JComboBox<>(
            new String[] { "All notifications", "Unread", "Read" });
    final JComboBox<String> dateSort = new JComboBox<>(new String[] { "Newest first", "Oldest first" });
    final DateFilter dateFilter = new DateFilter();
    List<Notification> notifications = List.of();

    void styleCombo(JComboBox<String> combo, int width) {
        combo.setFont(AppTheme.regular(12));
        combo.setPreferredSize(new Dimension(width, 40));
    }

    List<Notification> filteredNotifications() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String selectedKind = String.valueOf(kindFilter.getSelectedItem());
        String selectedRead = String.valueOf(readFilter.getSelectedItem());
        int sortMode = dateSort.getSelectedIndex();
        List<Notification> filtered = new ArrayList<>();
        for (Notification n : notifications) {
            if ("Unread".equals(selectedRead) && n.isRead()) {
                continue;
            }
            if ("Read".equals(selectedRead) && !n.isRead()) {
                continue;
            }
            if (!query.isEmpty()) {
                String haystack = (safe(n.getTitle()) + " " + safe(n.getMessage()) + " "
                        + safe(n.getRequestEquipment()) + " " + safe(n.getCategoryLabel()) + " "
                        + safe(n.getRequestCode()) + " "
                        + src.model.RequestStatus.departmentLabel(n.getDepartment()))
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) {
                    continue;
                }
            }
            if (!"All types".equals(selectedKind)
                    && !n.getCategoryLabel().equals(selectedKind)) {
                continue;
            }
            LocalDate createdDate = n.getCreatedAt() == null ? null : n.getCreatedAt().toLocalDate();
            if (!dateFilter.matches(createdDate)) {
                continue;
            }
            filtered.add(n);
        }
        Comparator<Notification> cmp = Comparator.comparing(Notification::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        if (sortMode <= 0) {
            cmp = cmp.reversed();
        }
        filtered.sort(cmp.thenComparing(Comparator.comparingLong(Notification::getNotificationId).reversed()));
        return filtered;
    }

    String safe(String value) {
        return value == null ? "" : value;
    }

    String requestLabel(Notification notification) {
        return notification.getRequestCode() != null
                ? notification.getRequestCode() : "#" + notification.getRequestId();
    }

    void openRequest(long requestId) {
        new SwingWorker<src.model.BorrowRequest, Void>() {
            @Override
            protected src.model.BorrowRequest doInBackground() throws Exception {
                return requestController.findRequestForUser(user, requestId);
            }

            @Override
            protected void done() {
                try {
                    String role = user.getRole() == null ? "" : user.getRole().toUpperCase();
                    String scope = "ICT_STAFF".equals(role) || "FACILITY_STAFF".equals(role)
                            ? role : null;
                    new RequestDetailsDialog(
                            javax.swing.SwingUtilities.getWindowAncestor(NotificationsPanelPart1.this),
                            get(), scope, user, requestController).setVisible(true);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    UiSupport.showMessageDialog(NotificationsPanelPart1.this,
                            cause.getMessage(), "Request unavailable",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    @FunctionalInterface
    interface ThrowingAction {
        void run() throws Exception;
    }

    JPanel message(String text, boolean error) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(11));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        card.add(label, BorderLayout.CENTER);
        return card;
    }

    String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
