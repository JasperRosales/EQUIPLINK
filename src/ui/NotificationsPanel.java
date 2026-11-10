package src.ui;

import src.controller.NotificationController;
import src.model.Notification;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NotificationsPanel extends JPanel {

    private final long userId;
    private final NotificationController notificationController;
    private final JPanel notificationList = new JPanel();
    private final PrimaryButton markAllRead = new PrimaryButton(
            "Mark all read", PrimaryButton.Style.SECONDARY);

    public NotificationsPanel(long userId, NotificationController notificationController) {
        this.userId = userId;
        this.notificationController = notificationController;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        JLabel heading = new JLabel("Your notifications");
        heading.setFont(AppTheme.semibold(18));
        heading.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel("Request updates and account activity.");
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
        PrimaryButton refresh = new PrimaryButton("Refresh", PrimaryButton.Style.SECONDARY);
        refresh.addActionListener(event -> reload());
        actions.add(refresh);
        pageHeader.add(titleBlock, BorderLayout.CENTER);
        pageHeader.add(actions, BorderLayout.EAST);
        add(pageHeader);
        add(Box.createVerticalStrut(14));

        notificationList.setOpaque(false);
        notificationList.setLayout(new BoxLayout(notificationList, BoxLayout.Y_AXIS));
        add(notificationList);
        reload();
    }

    private void reload() {
        notificationList.removeAll();
        notificationList.add(message("Loading notifications…", false));
        notificationList.revalidate();
        new SwingWorker<List<Notification>, Void>() {
            @Override
            protected List<Notification> doInBackground() throws Exception {
                return notificationController.findForUser(userId);
            }

            @Override
            protected void done() {
                try {
                    showNotifications(get());
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

    private void showNotifications(List<Notification> notifications) {
        notificationList.removeAll();
        long unread = notifications.stream().filter(notification -> !notification.isRead()).count();
        markAllRead.setEnabled(unread > 0);
        JPanel summary = new JPanel(new BorderLayout());
        summary.setOpaque(false);
        JLabel count = new JLabel(unread + " unread");
        count.setFont(AppTheme.semibold(11));
        count.setForeground(AppTheme.primary());
        summary.add(count, BorderLayout.WEST);
        notificationList.add(summary);
        notificationList.add(Box.createVerticalStrut(9));

        if (notifications.isEmpty()) {
            notificationList.add(message(
                    "You're all caught up. New request updates will appear here.",
                    false));
        } else {
            for (Notification notification : notifications) {
                notificationList.add(notificationCard(notification));
                notificationList.add(Box.createVerticalStrut(9));
            }
        }
        notificationList.revalidate();
        notificationList.repaint();
    }

    private JPanel notificationCard(Notification notification) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout(12, 5));
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(notification.getTitle());
        title.setFont(AppTheme.semibold(12));
        title.setForeground(AppTheme.foreground());
        JLabel message = new JLabel("<html><div style='width:520px'>"
                + escape(notification.getMessage()) + "</div></html>");
        message.setFont(AppTheme.regular(11));
        message.setForeground(AppTheme.muted());
        String date = notification.getCreatedAt().format(
                DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"));
        JLabel time = new JLabel(date
                + (notification.getRequestId() == null
                        ? "" : "  ·  Request #" + notification.getRequestId()));
        time.setFont(AppTheme.regular(10.5f));
        time.setForeground(AppTheme.muted());
        body.add(title);
        body.add(Box.createVerticalStrut(5));
        body.add(message);
        body.add(Box.createVerticalStrut(6));
        body.add(time);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
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
        card.add(body, BorderLayout.CENTER);
        card.add(actions, BorderLayout.EAST);
        return card;
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
                    reload();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    JOptionPane.showMessageDialog(
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
                    reload();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    markAllRead.setEnabled(true);
                    JOptionPane.showMessageDialog(
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
