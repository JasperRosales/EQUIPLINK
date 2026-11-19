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

public class NotificationsPanel extends NotificationsPanelPart2 implements Refreshable {

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

    NotificationsPanel(
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

    JPanel filtersRow() {
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

    @Override
    public void reloadData() {
        reload(true);
    }

    void deleteAllNotifications() {
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

    void markAllNotificationsRead() {
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
}
