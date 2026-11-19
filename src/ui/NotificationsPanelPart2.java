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

/** Part 2 of NotificationsPanel, split out to keep files short. */
public abstract class NotificationsPanelPart2 extends NotificationsPanelPart1 {

    void reload() {
        reload(false);
    }

    void reload(boolean quiet) {
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

    void showNotifications() {
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

    JPanel notificationCard(Notification notification) {
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

    /** Shows the full notification, marking it read as soon as it is opened. */
    void openDetails(Notification notification) {
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

    void deleteNotification(
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

    /**
     * Offers Undo immediately after a successful delete. The service enforces the
     * shared recovery window and puts every notification back with its original
     * read/unread state.
     */
    void offerUndo(List<Long> notificationIds, boolean all) {
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
                    UiSupport.showMessageDialog(NotificationsPanelPart2.this,
                            restored + (restored == 1
                                    ? " notification was restored with its original read state."
                                    : " notifications were restored with their original read states."),
                            "Notification restored", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    UiSupport.showMessageDialog(NotificationsPanelPart2.this,
                            cause.getMessage(), "Unable to restore notifications",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void runInBackground(ThrowingAction action, String errorTitle) {
        runInBackground(action, errorTitle, null, null);
    }

    void runInBackground(
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
                    UiSupport.showMessageDialog(NotificationsPanelPart2.this,
                            cause.getMessage(), errorTitle, JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }

    void markRead(Notification notification) {
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
                            NotificationsPanelPart2.this,
                            "Unable to update notification: " + cause.getMessage(),
                            "Notification update failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
