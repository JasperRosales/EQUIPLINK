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

public class NotificationsPanel extends JPanel implements Refreshable {

    private final long userId;
    private final src.model.User user;
    private final NotificationController notificationController;
    private final src.controller.BorrowRequestController requestController;
    private final Runnable onChanged;
    private final PrimaryButton deleteAll = new PrimaryButton(
            "Delete all", PrimaryButton.Style.DANGER);
    private final JPanel notificationList = new JPanel();
    private final PrimaryButton markAllRead = new PrimaryButton(
            "Mark all read", PrimaryButton.Style.SECONDARY);
    private final JTextField search = new JTextField(18);
    private final JComboBox<String> kindFilter = new JComboBox<>(
            new String[] { "All types", "Request", "Borrowing & Returns", "Account" });
    private final JComboBox<String> readFilter = new JComboBox<>(
            new String[] { "All notifications", "Unread", "Read" });
    private final JComboBox<String> dateSort = new JComboBox<>(new String[] { "Newest first", "Oldest first" });
    private final DateFilter dateFilter = new DateFilter();
    private List<Notification> notifications = List.of();

    public NotificationsPanel(long userId, NotificationController notificationController) {
        this(null, userId, notificationController, null, null);
    }

    public NotificationsPanel(
            src.model.User user,
            NotificationController notificationController,
            src.controller.BorrowRequestController requestController,
            Runnable onChanged) {
        this(user, user.getUserId(), notificationController, requestController, onChanged);
    }

    private NotificationsPanel(
            src.model.User user,
            long userId,
            NotificationController notificationController,
            src.controller.BorrowRequestController requestController,
            Runnable onChanged) {
        this.user = user;
        this.userId = userId;
        this.notificationController = notificationController;
        this.requestController = requestController;
        this.onChanged = onChanged == null ? () -> { } : onChanged;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        JLabel heading = new JLabel("Your notifications");
        heading.setFont(AppTheme.semibold(18));
        heading.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel("Request updates and account activity. Click a notification for full details.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        JPanel pageHeader = new JPanel(new BorderLayout(8, 8));
        pageHeader.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(heading);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(subtitle);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        markAllRead.addActionListener(event -> markAllNotificationsRead());
        actions.add(markAllRead);
        deleteAll.addActionListener(event -> deleteAllNotifications());
        actions.add(deleteAll);
        PrimaryButton refresh = new PrimaryButton("Refresh", PrimaryButton.Style.SECONDARY);
        refresh.addActionListener(event -> reload());
        actions.add(refresh);
        pageHeader.add(titleBlock, BorderLayout.CENTER);
        pageHeader.add(actions, BorderLayout.EAST);
        add(pageHeader);
        add(Box.createVerticalStrut(10));
        add(filtersRow());
        add(Box.createVerticalStrut(14));

        notificationList.setOpaque(false);
        notificationList.setLayout(new BoxLayout(notificationList, BoxLayout.Y_AXIS));
        add(notificationList);
        reload();
    }

    private JPanel filtersRow() {
        JPanel filters = new JPanel(new BorderLayout(8, 0));
        filters.setOpaque(false);
        UiSupport.styleInput(search);
        search.putClientProperty("JTextField.placeholderText",
                "Search title, message, or requested equipment");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(UiSupport.documentListener(this::showNotifications));
        filters.add(search, BorderLayout.CENTER);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        styleCombo(kindFilter, 150);
        styleCombo(readFilter, 110);
        styleCombo(dateSort, 140);
        kindFilter.addActionListener(event -> showNotifications());
        readFilter.addActionListener(event -> showNotifications());
        dateSort.addActionListener(event -> showNotifications());
        dateFilter.addChangeListener(this::showNotifications);
        tools.add(kindFilter);
        tools.add(readFilter);
        tools.add(dateFilter);
        tools.add(dateSort);
        filters.add(tools, BorderLayout.EAST);
        filters.setAlignmentX(LEFT_ALIGNMENT);
        return filters;
    }

    private void styleCombo(JComboBox<String> combo, int width) {
        combo.setFont(AppTheme.regular(12));
        combo.setPreferredSize(new Dimension(width, 40));
    }

    @Override
    public void reloadData() {
        reload(true);
    }

    private void reload() {
        reload(false);
    }

    private void reload(boolean quiet) {
        if (!quiet) {
            notificationList.removeAll();
            notificationList.add(message("Loading notifications…", false));
            notificationList.revalidate();
        }
        new SwingWorker<List<Notification>, Void>() {
            @Override
            protected List<Notification> doInBackground() throws Exception {
                return notificationController.findForUser(userId);
            }

            @Override
            protected void done() {
                try {
                    notifications = get();
                    showNotifications();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    notificationList.removeAll();
                    notificationList.add(message(
                            "Unable to load notifications: " + cause.getMessage(), true));
                    notificationList.revalidate();
                    notificationList.repaint();
                }
            }
        }.execute();
    }

    private void showNotifications() {
        notificationList.removeAll();
        long unread = notifications.stream().filter(n -> !n.isRead()).count();
        markAllRead.setEnabled(unread > 0);
        deleteAll.setEnabled(!notifications.isEmpty());
        List<Notification> filtered = filteredNotifications();
        JPanel summary = new JPanel(new BorderLayout());
        summary.setOpaque(false);
        JLabel count = new JLabel(unread + " unread · " + filtered.size() + " shown");
        count.setFont(AppTheme.semibold(11));
        count.setForeground(AppTheme.primary());
        summary.add(count, BorderLayout.WEST);
        notificationList.add(summary);
        notificationList.add(Box.createVerticalStrut(9));

        if (filtered.isEmpty()) {
            notificationList.add(message(
                    notifications.isEmpty()
                            ? "You're all caught up. New request updates will appear here."
                            : "No notifications match your search or filters.",
                    false));
        } else {
            for (Notification notification : filtered) {
                notificationList.add(notificationCard(notification));
                notificationList.add(Box.createVerticalStrut(9));
            }
        }
        notificationList.revalidate();
        notificationList.repaint();
    }

    private List<Notification> filteredNotifications() {
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private JPanel notificationCard(Notification notification) {
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
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { openDetails(notification); }
        });
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        card.setToolTipText("Click to view full details");
        card.setLayout(new BorderLayout(12, 5));
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(notification.getTitle());
        title.setFont(AppTheme.semibold(12));
        // Unread notifications use the accent color so they stand out from read ones.
        title.setForeground(notification.isRead() ? AppTheme.foreground() : AppTheme.primary());
        JLabel kind = new JLabel(notification.getCategoryLabel());
        kind.setFont(AppTheme.semibold(9.5f));
        kind.setForeground(AppTheme.primary());
        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.CENTER);
        titleRow.add(kind, BorderLayout.EAST);
        StringBuilder content = new StringBuilder();
        if (notification.getMessage() != null) {
            content.append(escape(notification.getMessage()));
        }
        if (!safe(notification.getRequestEquipment()).isBlank()) {
            content.append("<br><b>Requested equipment:</b> ")
                    .append(escape(notification.getRequestEquipment()));
        }
        if (!safe(notification.getRequestCategories()).isBlank()) {
            content.append("<br><b>Category:</b> ")
                    .append(escape(notification.getRequestCategories()));
        }
        JLabel message = new JLabel("<html><div style='width:520px'>"
                + content + "</div></html>");
        message.setFont(AppTheme.regular(11));
        message.setForeground(AppTheme.muted());
        String created = notification.getCreatedAt() == null ? "—"
                : notification.getCreatedAt().format(
                        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")) + " (PHT)";
        String req = notification.getRequestId() == null
                ? "" : "  ·  Request " + requestLabel(notification);
        if (notification.getDepartment() != null) {
            req += "  ·  " + src.model.RequestStatus.departmentLabel(notification.getDepartment());
        }
        if (notification.getEventDate() != null) {
            req += "  ·  Event " + notification.getEventDate().format(
                    DateTimeFormatter.ofPattern("MMM d, yyyy"));
        }
        JLabel time = new JLabel("Created " + created + req);
        time.setFont(AppTheme.regular(10.5f));
        time.setForeground(AppTheme.muted());
        body.add(titleRow);
        body.add(Box.createVerticalStrut(5));
        body.add(message);
        body.add(Box.createVerticalStrut(6));
        body.add(time);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        if (!notification.isRead()) {
            PrimaryButton markRead = new PrimaryButton("Mark read", PrimaryButton.Style.SECONDARY);
            markRead.addActionListener(event -> markRead(notification));
            actions.add(markRead);
        } else {
            JLabel read = new JLabel("Read");
            read.setFont(AppTheme.semibold(10));
            read.setForeground(AppTheme.success());
            actions.add(read);
        }
        PrimaryButton delete = new PrimaryButton("Delete", PrimaryButton.Style.DANGER);
        delete.setToolTipText("Remove this notification from your list");
        delete.addActionListener(event -> deleteNotification(notification, false, delete));
        actions.add(delete);
        card.add(body, BorderLayout.CENTER);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private String requestLabel(Notification notification) {
        return notification.getRequestCode() != null
                ? notification.getRequestCode() : "#" + notification.getRequestId();
    }

    /** Shows the full notification, marking it read as soon as it is opened. */
    private void openDetails(Notification notification) {
        if (!notification.isRead()) {
            runInBackground(() -> notificationController.markRead(
                    userId, notification.getNotificationId()), "Unable to update notification");
        }
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(this);
        NotificationDetailsDialog dialog = new NotificationDetailsDialog(
                owner, notification,
                notification.getRequestId() != null && requestController != null && user != null
                        ? () -> openRequest(notification.getRequestId()) : null,
                () -> deleteNotification(notification, true, null));
        dialog.setVisible(true);
    }

    private void openRequest(long requestId) {
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
                            javax.swing.SwingUtilities.getWindowAncestor(NotificationsPanel.this),
                            get(), scope, user, requestController).setVisible(true);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    UiSupport.showMessageDialog(NotificationsPanel.this,
                            cause.getMessage(), "Request unavailable",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void deleteNotification(
            Notification notification, boolean confirmed, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        if (!confirmed) {
            int answer = UiSupport.showConfirmDialog(this,
                    "Delete this notification? The related request is not affected.\n"
                            + "You can undo this for " + UndoPolicy.RECOVERY_LABEL + ".",
                    "Delete notification", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                UiSupport.endAction(source);
                return;
            }
        }
        long notificationId = notification.getNotificationId();
        runInBackground(() -> notificationController.delete(userId, notificationId),
                "Unable to delete notification",
                () -> offerUndo(List.of(notificationId), false), source);
    }

    private void deleteAllNotifications() {
        if (!UiSupport.beginAction(deleteAll)) {
            return;
        }
        List<Long> deletedIds = new ArrayList<>();
        for (Notification notification : notifications) {
            deletedIds.add(notification.getNotificationId());
        }
        int answer = UiSupport.showConfirmDialog(this,
                "Delete all " + deletedIds.size() + " notifications?\n"
                        + "Your requests, approvals, releases, and history are not affected.\n"
                        + "You can undo this for " + UndoPolicy.RECOVERY_LABEL + ".",
                "Delete all notifications", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            runInBackground(() -> notificationController.deleteAll(userId),
                    "Unable to delete notifications",
                    () -> offerUndo(deletedIds, true), deleteAll);
        } else {
            UiSupport.endAction(deleteAll);
        }
    }

    /**
     * Offers Undo immediately after a successful delete. The service enforces the
     * shared recovery window and puts every notification back with its original
     * read/unread state.
     */
    private void offerUndo(List<Long> notificationIds, boolean all) {
        if (notificationIds.isEmpty()) {
            return;
        }
        String label = all
                ? notificationIds.size() + " notifications were deleted."
                : "Notification was deleted.";
        int choice = UiSupport.showUndoDialog(this,
                label + "\nUndo is available for " + UndoPolicy.RECOVERY_LABEL + ".",
                "Notification deleted");
        if (choice != 0) {
            return;
        }
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                if (notificationIds.size() == 1) {
                    notificationController.restore(userId, notificationIds.get(0));
                    return 1;
                }
                return notificationController.restoreAll(userId, notificationIds);
            }

            @Override
            protected void done() {
                try {
                    int restored = get();
                    reload(true);
                    onChanged.run();
                    UiSupport.showMessageDialog(NotificationsPanel.this,
                            restored + (restored == 1
                                    ? " notification was restored with its original read state."
                                    : " notifications were restored with their original read states."),
                            "Notification restored", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    UiSupport.showMessageDialog(NotificationsPanel.this,
                            cause.getMessage(), "Unable to restore notifications",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void runInBackground(ThrowingAction action, String errorTitle) {
        runInBackground(action, errorTitle, null, null);
    }

    private void runInBackground(
            ThrowingAction action, String errorTitle, Runnable onSuccess,
            javax.swing.AbstractButton source) {
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
                    reload(true);
                    onChanged.run();
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    UiSupport.showMessageDialog(NotificationsPanel.this,
                            cause.getMessage(), errorTitle, JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private void markRead(Notification notification) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                notificationController.markRead(userId, notification.getNotificationId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    reload(true);
                    onChanged.run();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    UiSupport.showMessageDialog(
                            NotificationsPanel.this,
                            "Unable to update notification: " + cause.getMessage(),
                            "Notification update failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void markAllNotificationsRead() {
        markAllRead.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                notificationController.markAllRead(userId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    reload(true);
                    onChanged.run();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    markAllRead.setEnabled(true);
                    UiSupport.showMessageDialog(
                            NotificationsPanel.this,
                            "Unable to mark notifications as read: " + cause.getMessage(),
                            "Notification update failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private JPanel message(String text, boolean error) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(11));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        card.add(label, BorderLayout.CENTER);
        return card;
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
