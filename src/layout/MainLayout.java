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

public class MainLayout extends MainLayoutPart2 {

        public MainLayout(User user, AuthController authController) {
                this(user, authController,
                                new BorrowRequestController(new BorrowRequestService()),
                                new EquipmentController(new EquipmentService()),
                                new NotificationController(new NotificationService()));
        }

        public MainLayout(
                        User user,
                        AuthController authController,
                        BorrowRequestController requestController,
                        EquipmentController equipmentController) {
                this(user, authController, requestController, equipmentController,
                                new NotificationController(new NotificationService()));
        }

        public MainLayout(
                        User user,
                        AuthController authController,
                        BorrowRequestController requestController,
                        EquipmentController equipmentController,
                        NotificationController notificationController) {
                this("EquipLink", user.getFullName(), displayRole(user.getRole()),
                                navigationFor(user.getRole()), user, authController,
                                requestController, equipmentController, notificationController);
        }

        public MainLayout(
                        String systemName,
                        String userName,
                        String userRole,
                        String[] navigationItems) {
                this(systemName, userName, userRole, navigationItems, null, null,
                                new BorrowRequestController(new BorrowRequestService()),
                                new EquipmentController(new EquipmentService()),
                                new NotificationController(new NotificationService()));
        }

        MainLayout(
                        String systemName,
                        String userName,
                        String userRole,
                        String[] navigationItems,
                        User user,
                        AuthController authController,
                        BorrowRequestController requestController,
                        EquipmentController equipmentController,
                        NotificationController notificationController) {
                this.user = user;
                this.notificationsEnabled = user == null || user.isNotificationsEnabled();
                this.authController = authController;
                this.requestController = requestController;
                this.equipmentController = equipmentController;
                this.notificationController = notificationController;
                setLayout(new BorderLayout());
                setBackground(AppTheme.background());

                sidebar = new JPanel(new BorderLayout());
                sidebar.setBackground(AppTheme.sidebar());
                sidebar.setPreferredSize(new Dimension(252, 0));
                sidebar.add(createSidebarTop(systemName, navigationItems), BorderLayout.CENTER);
                sidebar.add(createProfile(userName, userRole), BorderLayout.SOUTH);

                mainPanel = new JPanel(new BorderLayout());
                mainPanel.setBackground(AppTheme.background());
                header = new JPanel(new BorderLayout(18, 0));
                header.setBackground(AppTheme.background());
                header.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                                javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.border()),
                                new EmptyBorder(14, 30, 14, 28)));
                header.setPreferredSize(new Dimension(0, 84));

                JPanel pageLabels = new JPanel();
                pageLabels.setOpaque(false);
                pageLabels.setLayout(new BoxLayout(pageLabels, BoxLayout.Y_AXIS));
                pageTitle = new JLabel("Dashboard");
                pageTitle.setFont(AppTheme.semibold(20));
                pageTitle.setForeground(AppTheme.foreground());
                pageDescription = new JLabel("");
                pageDescription.setFont(AppTheme.regular(12));
                pageDescription.setForeground(AppTheme.muted());
                pageLabels.add(pageTitle);
                pageLabels.add(Box.createVerticalStrut(2));
                pageLabels.add(pageDescription);
                header.add(pageLabels, BorderLayout.WEST);
                header.add(createHeaderActions(userName, userRole), BorderLayout.EAST);

                pageContainer = new JPanel(new BorderLayout());
                pageContainer.setBackground(AppTheme.background());
                pageContainer.setBorder(new EmptyBorder(24, 30, 24, 30));
                mainPanel.add(header, BorderLayout.NORTH);
                mainPanel.add(pageContainer, BorderLayout.CENTER);

                add(sidebar, BorderLayout.WEST);
                add(mainPanel, BorderLayout.CENTER);
                showPage("Dashboard");
                schedulePendingReminder();
                startLiveSync();
        }

        JPanel createSidebarTop(String systemName, String[] navigationItems) {
                JPanel top = new JPanel();
                top.setBackground(AppTheme.sidebar());
                top.setBorder(new EmptyBorder(22, 14, 15, 14));
                top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

                JPanel brand = new JPanel(new BorderLayout(11, 0));
                brand.setOpaque(false);
                brand.setAlignmentX(LEFT_ALIGNMENT);
                brand.setBorder(new EmptyBorder(0, 6, 0, 0));
                brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                JLabel logo = new JLabel(src.ui.BrandLogo.badge(36, AppTheme.LIGHT_BG));
                logo.setPreferredSize(new Dimension(36, 36));
                JPanel brandText = new JPanel(new java.awt.GridLayout(2, 1));
                brandText.setOpaque(false);
                JLabel name = new JLabel(systemName);
                name.setFont(AppTheme.semibold(16));
                name.setForeground(Color.WHITE);
                JLabel tagline = new JLabel("Campus Equipment System");
                tagline.setFont(AppTheme.regular(10));
                tagline.setForeground(AppTheme.sidebarMuted());
                brandText.add(name);
                brandText.add(tagline);
                brand.add(logo, BorderLayout.WEST);
                brand.add(brandText, BorderLayout.CENTER);
                top.add(brand);
                top.add(Box.createVerticalStrut(28));

                JLabel menuLabel = new JLabel("MENU");
                menuLabel.setFont(AppTheme.semibold(10));
                menuLabel.setForeground(AppTheme.sidebarMuted());
                menuLabel.setBorder(new EmptyBorder(0, 14, 8, 0));
                menuLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                menuLabel.setAlignmentX(LEFT_ALIGNMENT);
                top.add(menuLabel);

                navigationPanel = new JPanel();
                navigationPanel.setBackground(AppTheme.sidebar());
                navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.Y_AXIS));
                navigationPanel.setAlignmentX(LEFT_ALIGNMENT);
                for (String item : navigationItems) {
                        NavigationButton button = new NavigationButton(item);
                        button.addActionListener(event -> showPage(item));
                        navigationButtons.put(item, button);
                        navigationPanel.add(button);
                        navigationPanel.add(Box.createVerticalStrut(4));
                }
                JScrollPane navigationScroll = UiSupport.scroll(navigationPanel);
                navigationScroll.setOpaque(false);
                navigationScroll.getViewport().setOpaque(false);
                navigationScroll.setAlignmentX(LEFT_ALIGNMENT);
                top.add(navigationScroll);
                return top;
        }

        JPanel createHeaderActions(String name, String role) {
                JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 2));
                actions.setOpaque(false);
                notificationButton = new PrimaryButton("Notifications",
                                PrimaryButton.Style.SECONDARY);
                notificationButton.setToolTipText("Open notifications");
                notificationButton.addActionListener(event -> showPage("Notifications"));
                actions.add(notificationButton);

                javax.swing.JSeparator divider = new javax.swing.JSeparator(SwingConstants.VERTICAL);
                divider.setPreferredSize(new Dimension(1, 30));
                actions.add(divider);
                actions.add(new src.ui.components.Avatar(initials(name), AppTheme.primary(), 999));
                JPanel roleBadge = new JPanel(new GridLayout(0, 1, 0, 1));
                roleBadge.setOpaque(false);
                headerUserName = new JLabel(name);
                headerUserName.setFont(AppTheme.semibold(12));
                headerUserName.setForeground(AppTheme.foreground());
                headerUserRole = new JLabel(role);
                headerUserRole.setFont(AppTheme.regular(10.5f));
                headerUserRole.setForeground(AppTheme.muted());
                roleBadge.add(headerUserName);
                roleBadge.add(headerUserRole);
                actions.add(roleBadge);
                return actions;
        }

        void startLiveSync() {
                if (user == null || notificationController == null) {
                        return;
                }
                refreshNotificationBadge();
                liveSyncTimer = new javax.swing.Timer(LIVE_SYNC_MS, event -> pollForChanges());
                liveSyncTimer.setRepeats(true);
                liveSyncTimer.start();
        }

        @Override
        public void addNotify() {
                super.addNotify();
                if (liveSyncTimer != null && !liveSyncTimer.isRunning()) {
                        liveSyncTimer.start();
                }
        }

        @Override
        public void removeNotify() {
                if (liveSyncTimer != null) {
                        liveSyncTimer.stop();
                }
                if (reminderTimer != null) {
                        reminderTimer.stop();
                }
                super.removeNotify();
        }

        boolean isApprover() {
                String role = user == null ? "" : user.getRole();
                String normalized = role == null ? "" : role.trim().toUpperCase().replace(' ', '_');
                return "ICT_STAFF".equals(normalized)
                                || "FACILITY_STAFF".equals(normalized)
                                || "ADMIN".equals(normalized)
                                || "DIRECTOR".equals(normalized);
        }

        void schedulePendingReminder() {
                if (!isApprover() || requestController == null || notificationController == null) {
                        return;
                }
                checkPendingReminder(true);
                scheduleFourPmReminder();
        }

        void scheduleFourPmReminder() {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                java.time.LocalDateTime next = now.with(java.time.LocalTime.of(16, 0));
                if (!now.isBefore(next)) {
                        next = next.plusDays(1);
                }
                long delay = Math.max(1000L, java.time.Duration.between(now, next).toMillis());
                int timerDelay = (int) Math.min(delay, Integer.MAX_VALUE);
                reminderTimer = new javax.swing.Timer(timerDelay, event -> {
                        checkPendingReminder(false);
                        scheduleFourPmReminder();
                });
                reminderTimer.setRepeats(false);
                reminderTimer.start();
        }

        void checkPendingReminder(boolean onLogin) {
                if (!isApprover()) {
                        return;
                }
                new javax.swing.SwingWorker<Integer, Void>() {
                        @Override
                        protected Integer doInBackground() throws Exception {
                                int pending = requestController.countQueueForRole(user.getRole());
                                if (pending > 0 && !notificationController.hasPendingReminderToday(
                                                user.getUserId())) {
                                        notificationController.createPendingReminder(
                                                        user.getUserId(), pending);
                                }
                                return pending;
                        }

                        @Override
                        protected void done() {
                                try {
                                        int pending = get();
                                        if (pending > 0 && notificationsEnabled) {
                                                String msg = pending == 1
                                                                ? "You have 1 pending request waiting for your action."
                                                                : "You have " + pending
                                                                                + " pending requests waiting for your action.";
                                                if (!onLogin) {
                                                        msg += "\n(4:00 PM reminder)";
                                                }
                                                UiSupport.showMessageDialog(
                                                                MainLayout.this,
                                                                msg,
                                                                "Pending request reminder",
                                                                javax.swing.JOptionPane.INFORMATION_MESSAGE);
                                        }
                                } catch (Exception ignored) {
                                }
                        }
                }.execute();
        }
}
