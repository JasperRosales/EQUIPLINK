package src.ui;

import src.controller.NotificationController;
import src.model.Notification;
import src.ui.components.DateFilter;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

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

public class NotificationsPanel extends JPanel {

    private final long userId;
    private final NotificationController notificationController;
    private final JPanel notificationList = new JPanel();
    private final PrimaryButton markAllRead = new PrimaryButton(
            "Mark all read", PrimaryButton.Style.SECONDARY);
    private final JTextField search = new JTextField(18);
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[] {
            "All", "Unread", "Request", "Borrowing & Returns", "Account" });
    private final JComboBox<String> dateSort = new JComboBox<>(new String[] { "Newest first", "Oldest first" });
    private final DateFilter dateFilter = new DateFilter();
    private List<Notification> notifications = List.of();

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
        styleCombo(typeFilter, 175);
        styleCombo(dateSort, 140);
        typeFilter.addActionListener(event -> showNotifications());
        dateSort.addActionListener(event -> showNotifications());
        dateFilter.addChangeListener(this::showNotifications);
        tools.add(typeFilter);
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
        String type = String.valueOf(typeFilter.getSelectedItem());
        int sortMode = dateSort.getSelectedIndex();
        List<Notification> filtered = new ArrayList<>();
        for (Notification n : notifications) {
            if ("Unread".equals(type) && n.isRead()) {
                continue;
            }
            if (!"All".equals(type) && !"Unread".equals(type) && !matchesType(n, type)) {
                continue;
            }
            if (!query.isEmpty()) {
                String haystack = (safe(n.getTitle()) + " " + safe(n.getMessage()) + " "
                        + safe(n.getRequestEquipment()) + " " + safe(n.getRequestCategories()))
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) {
                    continue;
                }
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

    private boolean matchesType(Notification n, String type) {
        String title = safe(n.getTitle()).toLowerCase(Locale.ROOT);
        String msg = safe(n.getMessage()).toLowerCase(Locale.ROOT);
        boolean account = title.contains("password") || title.contains("account")
                || msg.contains("password") || msg.contains("account");
        boolean borrowReturn = title.contains("release") || title.contains("return")
                || title.contains("completed") || msg.contains("release")
                || msg.contains("returned") || msg.contains("return")
                || msg.contains("completed transaction") || msg.contains("borrowed");
        boolean request = n.getRequestId() != null || title.contains("request")
                || title.contains("approval") || title.contains("submitted")
                || msg.contains("request") || msg.contains("approval");
        if ("Account".equals(type)) return account;
        if ("Borrowing & Returns".equals(type)) return borrowReturn && !account;
        if ("Request".equals(type)) return request && !borrowReturn && !account;
        return true;
    }

    private List<String> splitValues(String csv) {
        List<String> values = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return values;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
                ? "" : "  ·  Request #" + notification.getRequestId();
        if (notification.getEventDate() != null) {
            req += "  ·  Event " + notification.getEventDate().format(
                    DateTimeFormatter.ofPattern("MMM d, yyyy"));
        }
        JLabel time = new JLabel("Created " + created + req);
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
