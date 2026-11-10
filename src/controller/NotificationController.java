package src.controller;

import src.model.Notification;
import src.service.NotificationService;

import java.util.List;

public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public List<Notification> findForUser(long userId) throws Exception {
        return notificationService.findForUser(userId);
    }

    public void markRead(long userId, long notificationId) throws Exception {
        notificationService.markRead(userId, notificationId);
    }

    public void markAllRead(long userId) throws Exception {
        notificationService.markAllRead(userId);
    }

    public boolean hasPendingReminderToday(long userId) throws Exception {
        return notificationService.hasPendingReminderToday(userId);
    }

    public void createPendingReminder(long userId, int pendingCount)
            throws Exception {
        notificationService.createPendingReminder(userId, pendingCount);
    }
}
