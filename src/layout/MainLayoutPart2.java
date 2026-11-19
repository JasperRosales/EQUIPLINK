package src.layout;

import src.controller.AuthController;
import src.controller.BorrowRequestController;
import src.controller.EquipmentController;
import src.controller.NotificationController;
import src.model.BorrowRequest;
import src.model.User;
import src.service.BorrowRequestService;
import src.service.EquipmentService;
import src.service.NotificationService;
import src.ui.AppTheme;
import src.ui.BorrowingHistoryPanel;
import src.ui.DashboardPanel;
import src.ui.EquipmentInventoryPanel;
import src.ui.EquipmentRequestPanel;
import src.ui.PasswordResetRequestsPanel;
import src.ui.MyRequestsPanel;
import src.ui.NotificationsPanel;
import src.ui.RequestQueuePanel;
import src.ui.RoomManagementPanel;
import src.ui.SettingsPanel;
import src.ui.UserManagementPanel;
import src.ui.UiSupport;
import src.ui.components.NavigationButton;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/** Part 2 of MainLayout, split out to keep files short. */
public abstract class MainLayoutPart2 extends MainLayoutPart1 {

        /**
         * Opens a page. "Page::Filter" (e.g. "All Requests::Declined") also preselects
         * a status filter, so dashboard counters open the matching filtered list.
         */
        public void showPage(String target) {
                currentPage = target;
                String item = target;
                pageFilter = null;
                int split = target.indexOf("::");
                if (split >= 0) {
                        item = target.substring(0, split);
                        pageFilter = target.substring(split + 2);
                }
                String title = pageTitleFor(item);
                pageTitle.setText(title);
                pageDescription.setText(pageDescriptionFor(item));
                setActiveNavigation(navigationItemFor(item));
                pageContainer.removeAll();
                currentContent = createPage(item);
                lastReload = System.currentTimeMillis();
                pageContainer.add(new src.ui.components.FadeInPanel(
                                UiSupport.scroll(currentContent)), BorderLayout.CENTER);
                pageContainer.revalidate();
                pageContainer.repaint();
        }

        javax.swing.JComponent createPage(String item) {
                if (user == null) {
                        return infoPage(item, "Sign in to access your EquipLink workspace.");
                }
                switch (item) {
                        case "Dashboard":
                                return new DashboardPanel(
                                                user, requestController, equipmentController, this::showPage);
                        case "Request Equipment":
                                if (isBorrower()) {
                                        return requestForm(null);
                                }
                                return infoPage(item, "Equipment requests are available to borrower accounts.");
                        case "My Requests":
                                if (isBorrower()) {
                                        return new MyRequestsPanel(
                                                        user.getUserId(),
                                                        requestController,
                                                        this::editRequest,
                                                        this::refreshCurrentPage,
                                                        pageFilter);
                                }
                                return infoPage(item, "This page is available to borrower accounts.");
                        case "Pending Requests":
                                if (isBorrower()) {
                                        return new MyRequestsPanel(
                                                        user.getUserId(), requestController,
                                                        this::editRequest, this::refreshCurrentPage,
                                                        "PENDING");
                                }
                                return infoPage(item, "This page is available to borrower accounts.");
                        case "Borrowed Requests":
                                if (isBorrower()) {
                                        return new MyRequestsPanel(
                                                        user.getUserId(), requestController,
                                                        this::editRequest, this::refreshCurrentPage,
                                                        "BORROWED");
                                }
                                return infoPage(item, "This page is available to borrower accounts.");
                        case "Borrowing Request":
                                if (isBorrower()) {
                                        return new BorrowingHistoryPanel(user.getUserId(), requestController);
                                }
                                return infoPage(item, "Review borrowing history and activity.");
                        case "Settings":
                                return new SettingsPanel(
                                                user, authController, notificationsEnabled,
                                                this::refreshCurrentPage,
                                                this::saveNotificationPreference);
                        case "Notifications":
                                return new NotificationsPanel(
                                                user, notificationController, requestController,
                                                this::refreshNotificationBadge);
                        case "Available Equipment":
                                return new EquipmentInventoryPanel(
                                                user, equipmentController, this::requestItems);
                        case "Inventory":
                        case "Equipment":
                                return new EquipmentInventoryPanel(user, equipmentController);
                        case "Requests":
                                if (isStaff()) {
                                        return new RequestQueuePanel(
                                                        user, requestController,
                                                        RequestQueuePanel.Mode.STAFF_QUEUE);
                                }
                                return infoPage(item, "This page is available to equipment staff.");
                        case "Schedule & Assignment":
                                if (isStaff()) {
                                        return new RequestQueuePanel(
                                                        user, requestController,
                                                        RequestQueuePanel.Mode.SCHEDULE_ASSIGNMENT);
                                }
                                return infoPage(item, "This page is available to equipment staff.");
                        case "Transaction History":
                                if (isStaff() || isDirectorRole() || isAdminRole()) {
                                        return new RequestQueuePanel(
                                                        user, requestController,
                                                        RequestQueuePanel.Mode.TRANSACTION_HISTORY,
                                                        pageFilter);
                                }
                                return infoPage(item, "This page is available to staff and administrators.");
                        case "Borrowing Requests":
                                if ("ADMIN".equals(normalizeRole(user.getRole()))) {
                                        return new RequestQueuePanel(
                                                        user, requestController,
                                                        RequestQueuePanel.Mode.ADMIN_REVIEW);
                                }
                                return infoPage(item, "This page is available to facility administrators.");
                        case "Request Approval":
                                if ("DIRECTOR".equals(normalizeRole(user.getRole()))) {
                                        return new RequestQueuePanel(
                                                        user, requestController,
                                                        RequestQueuePanel.Mode.DIRECTOR_REVIEW);
                                }
                                return infoPage(item, "This page is available to the campus director.");
                        case "All Requests":
                                if (isBorrower()) {
                                        return new MyRequestsPanel(
                                                        user.getUserId(), requestController,
                                                        this::editRequest, this::refreshCurrentPage,
                                                        pageFilter);
                                }
                                if (isStaff() || isDirectorRole()) {
                                        // Facility Staff no longer has a separate All Requests
                                        // page, and the Director's page is Transaction History.
                                        return new RequestQueuePanel(
                                                        user, requestController,
                                                        RequestQueuePanel.Mode.TRANSACTION_HISTORY,
                                                        pageFilter);
                                }
                                return new RequestQueuePanel(
                                                user, requestController,
                                                RequestQueuePanel.Mode.ALL_REQUESTS, pageFilter);
                        case "Password Requests":
                                String role = normalizeRole(user.getRole());
                                if ("ICT_STAFF".equals(role)) {
                                        return new PasswordResetRequestsPanel(user, authController);
                                }
                                return infoPage(item, "This page is available to ICT system administrators.");
                        case "User Management":
                                if ("ICT_STAFF".equals(normalizeRole(user.getRole()))) {
                                        return new UserManagementPanel(user, authController);
                                }
                                return infoPage(item, "This page is available to ICT system administrators.");
                        case "Venue Management":
                                if ("ICT_STAFF".equals(normalizeRole(user.getRole()))) {
                                        return new RoomManagementPanel(user);
                                }
                                return infoPage(item, "This page is available to ICT system administrators.");
                        default:
                                return infoPage(item,
                                                "This workspace is ready for " + item.toLowerCase()
                                                                + " tools and records.");
                }
        }

        EquipmentRequestPanel requestForm(BorrowRequest request) {
                return requestForm(request, java.util.List.of());
        }

        EquipmentRequestPanel requestForm(
                        BorrowRequest request, java.util.List<src.model.EquipmentRequestItem> items) {
                return new EquipmentRequestPanel(
                                user, requestController, equipmentController, request,
                                () -> showPage("My Requests"), items);
        }

        /** Opens the request form prefilled with items picked from Available Equipment. */
        void requestItems(java.util.List<src.model.EquipmentRequestItem> items) {
                currentPage = "Request Equipment";
                pageTitle.setText("Request Equipment");
                pageDescription.setText(items.size() + " item(s) added from Available Equipment");
                setActiveNavigation("Request Equipment");
                pageContainer.removeAll();
                pageContainer.add(new src.ui.components.FadeInPanel(
                                UiSupport.scroll(requestForm(null, items))), BorderLayout.CENTER);
                pageContainer.revalidate();
                pageContainer.repaint();
        }

        void editRequest(BorrowRequest request) {
                currentPage = "My Requests";
                pageTitle.setText("Edit request");
                pageDescription.setText("Update details for " + request.getRequestCode());
                setActiveNavigation("My Requests");
                pageContainer.removeAll();
                pageContainer.add(new src.ui.components.FadeInPanel(
                                UiSupport.scroll(requestForm(request))), BorderLayout.CENTER);
                pageContainer.revalidate();
                pageContainer.repaint();
        }

        void refreshCurrentPage() {
                applyTheme();
                showPage(currentPage);
        }

        /** Reloads the visible page in place when requests or notifications changed. */
        void pollForChanges() {
                if (syncing || !isShowing()) {
                        return;
                }
                syncing = true;
                boolean sweep = System.currentTimeMillis() - lastOverdueSweep > OVERDUE_SWEEP_MS;
                new javax.swing.SwingWorker<Object[], Void>() {
                        @Override
                        protected Object[] doInBackground() throws Exception {
                                if (sweep) {
                                        try {
                                                notificationController.runOverdueSweep();
                                        } catch (Exception ignored) {
                                                // The sweep is retried on the next cycle.
                                        }
                                }
                                return new Object[] {
                                                notificationController.changeToken(user.getUserId()),
                                                notificationController.countUnread(user.getUserId())
                                };
                        }

                        @Override
                        protected void done() {
                                syncing = false;
                                if (sweep) {
                                        lastOverdueSweep = System.currentTimeMillis();
                                }
                                try {
                                        Object[] result = get();
                                        String token = (String) result[0];
                                        updateNotificationButton((Integer) result[1]);
                                        boolean changed = lastChangeToken != null
                                                        && !lastChangeToken.equals(token);
                                        boolean stale = System.currentTimeMillis() - lastReload
                                                        > TIME_REFRESH_MS;
                                        java.time.LocalDate today = java.time.LocalDate.now(
                                                java.time.ZoneId.of("Asia/Manila"));
                                        boolean newDay = !today.equals(lastSyncDate);
                                        lastSyncDate = today;
                                        lastChangeToken = token;
                                        if ((changed || stale || newDay)
                                                        && currentContent instanceof src.ui.Refreshable) {
                                                lastReload = System.currentTimeMillis();
                                                ((src.ui.Refreshable) currentContent).reloadData();
                                        }
                                } catch (Exception ignored) {
                                        // Database briefly unavailable; try again next cycle.
                                }
                        }
                }.execute();
        }

        void refreshNotificationBadge() {
                if (user == null || notificationController == null) {
                        return;
                }
                new javax.swing.SwingWorker<Integer, Void>() {
                        @Override
                        protected Integer doInBackground() throws Exception {
                                return notificationController.countUnread(user.getUserId());
                        }

                        @Override
                        protected void done() {
                                try {
                                        updateNotificationButton(get());
                                } catch (Exception ignored) {
                                }
                        }
                }.execute();
        }
}
