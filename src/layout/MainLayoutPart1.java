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

/** Part 1 of MainLayout, split out to keep files short. */
public abstract class MainLayoutPart1 extends JPanel {

        User user;
        AuthController authController;
        BorrowRequestController requestController;
        EquipmentController equipmentController;
        NotificationController notificationController;
        JPanel sidebar;
        JPanel navigationPanel;
        JPanel header;
        JPanel mainPanel;
        JPanel pageContainer;
        JLabel pageTitle;
        JLabel pageDescription;
        PrimaryButton notificationButton;
        JLabel headerUserName;
        JLabel headerUserRole;
        final Map<String, NavigationButton> navigationButtons = new LinkedHashMap<>();
        boolean notificationsEnabled = true;
        Runnable logoutAction;
        String currentPage = "Dashboard";
        javax.swing.Timer reminderTimer;
        /** Polls for request/notification changes so every dashboard stays in sync. */
        javax.swing.Timer liveSyncTimer;
        javax.swing.JComponent currentContent;
        String pageFilter;
        String lastChangeToken;
        boolean syncing;
        long lastOverdueSweep;
        long lastReload = System.currentTimeMillis();
        java.time.LocalDate lastSyncDate =
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila"));
        static final int LIVE_SYNC_MS = 8000;
        static final long OVERDUE_SWEEP_MS = 60_000L;
        static final long TIME_REFRESH_MS = 5 * 60_000L;

        JPanel createProfile(String name, String role) {
                JPanel profile = new JPanel(new BorderLayout(10, 0));
                profile.setBackground(AppTheme.sidebar());
                profile.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                                javax.swing.BorderFactory.createMatteBorder(
                                                1, 0, 0, 0, AppTheme.sidebarDivider()),
                                new EmptyBorder(14, 18, 16, 16)));
                JLabel initials = new src.ui.components.Avatar(
                                initials(name), AppTheme.sidebarShadowLight(), 999);
                initials.setFont(AppTheme.semibold(11));
                initials.setPreferredSize(new Dimension(36, 36));

                JPanel identity = new JPanel();
                identity.setOpaque(false);
                identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
                JLabel profileName = new JLabel(name);
                profileName.setFont(AppTheme.semibold(11.5f));
                profileName.setForeground(Color.WHITE);
                JLabel profileRole = new JLabel(role);
                profileRole.setFont(AppTheme.regular(10.5f));
                profileRole.setForeground(AppTheme.sidebarMuted());
                identity.add(profileName);
                identity.add(Box.createVerticalStrut(3));
                identity.add(profileRole);
                profile.add(initials, BorderLayout.WEST);
                profile.add(identity, BorderLayout.CENTER);

                JButton logout = new JButton("Log out");
                logout.setToolTipText("Log out");
                logout.setFont(AppTheme.semibold(10.5f));
                logout.setForeground(AppTheme.sidebarText());
                logout.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                logout.setIconTextGap(4);
                logout.setBorder(new EmptyBorder(3, 0, 3, 0));
                logout.setContentAreaFilled(false);
                logout.setFocusPainted(false);
                logout.addActionListener(event -> {
                        if (logoutAction != null) {
                                logoutAction.run();
                        }
                });
                profile.add(logout, BorderLayout.EAST);
                return profile;
        }

        public static String[] navigationFor(String role) {
                switch (normalizeRole(role)) {
                        case "BORROWER":
                                return new String[] {
                                                "Dashboard", "Available Equipment", "Request Equipment", "My Requests",
                                                "Borrowing Request", "Notifications", "Settings"
                                };
                        case "ICT_STAFF":
                                return new String[] {
                                                "Dashboard", "Inventory", "Requests",
                                                "Schedule & Assignment", "Transaction History",
                                                "Password Requests", "User Management",
                                                "Venue Management",
                                                "Notifications", "Settings"
                                };
                        case "FACILITY_STAFF":
                                return new String[] {
                                                "Dashboard", "Inventory", "Requests",
                                                "Schedule & Assignment", "Transaction History",
                                                "Notifications", "Settings"
                                };
                        case "DIRECTOR":
                                return new String[] {
                                                "Dashboard", "Request Approval", "Transaction History",
                                                "Notifications", "Settings"
                                };
                        case "ADMIN":
                                return new String[] {
                                                "Dashboard", "Inventory", "Borrowing Requests",
                                                "All Requests", "Notifications", "Settings"
                                };
                        default:
                                return new String[] {
                                                "Dashboard", "Inventory", "Borrowing Requests",
                                                "All Requests", "Password Requests", "Notifications", "Settings"
                                };
                }
        }

        static String normalizeRole(String role) {
                return role == null ? "" : role.trim().toUpperCase().replace(' ', '_');
        }

        static String displayRole(String role) {
                switch (normalizeRole(role)) {
                        case "BORROWER":
                                return "Borrower";
                        case "ICT_STAFF":
                                return "System Admin";
                        case "FACILITY_STAFF":
                                return "Facility Staff";
                        case "DIRECTOR":
                                return "Campus Director";
                        case "ADMIN":
                                return "Facility Admin";
                        default:
                                return role == null || role.isBlank() ? "User" : role;
                }
        }

        String initials(String name) {
                if (name == null || name.isBlank()) {
                        return "EL";
                }
                String[] parts = name.trim().split("\\s+");
                return parts.length == 1
                                ? parts[0].substring(0, 1).toUpperCase()
                                : (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                                                .toUpperCase();
        }

        javax.swing.JComponent infoPage(String title, String detail) {
                SurfacePanel panel = new SurfacePanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                JLabel heading = new JLabel(title);
                heading.setFont(AppTheme.semibold(16));
                heading.setForeground(AppTheme.foreground());
                JLabel description = new JLabel(detail);
                description.setFont(AppTheme.regular(11));
                description.setForeground(AppTheme.muted());
                panel.add(heading);
                panel.add(Box.createVerticalStrut(7));
                panel.add(description);
                return panel;
        }

        String pageTitleFor(String item) {
                if ("Borrowing Request".equals(item)) {
                        return "Borrowing History";
                }
                if ("Pending Requests".equals(item) || "Borrowed Requests".equals(item)) {
                        return "My Requests";
                }
                return item;
        }

        String pageDescriptionFor(String item) {
                switch (item) {
                        case "Dashboard":
                                return "Overview of your EquipLink activity";
                        case "Request Equipment":
                                return "Create a request using live campus inventory";
                        case "Available Equipment":
                                return "Browse items on hand and request several at once";
                        case "Inventory":
                                return "Live stock for your equipment group";
                        case "My Requests":
                                return "Track pending requests and equipment you have borrowed";
                        case "Pending Requests":
                                return "Requests waiting for review or action";
                        case "Borrowed Requests":
                                return "Equipment currently released to you";
                        case "Borrowing Request":
                                return "Your completed, cancelled, and declined requests";
                        case "Transaction History":
                                return "Pending, approved, declined, overdue, and completed records";
                        case "Settings":
                                return "Appearance, notifications, and account security";
                        case "Notifications":
                                return "Stay up to date on request activity";
                        default:
                                return "Manage " + item.toLowerCase() + " in EquipLink";
                }
        }

        boolean isBorrower() {
                return "BORROWER".equals(normalizeRole(user.getRole()));
        }

        boolean isStaff() {
                String role = normalizeRole(user.getRole());
                return "ICT_STAFF".equals(role) || "FACILITY_STAFF".equals(role);
        }

        boolean isDirectorRole() {
                return "DIRECTOR".equals(normalizeRole(user.getRole()));
        }

        boolean isAdminRole() {
                return "ADMIN".equals(normalizeRole(user.getRole()));
        }

        String navigationItemFor(String page) {
                if (user != null && (isStaff() || isDirectorRole())
                        && "All Requests".equals(page)) {
                        return "Transaction History";
                }
                if (user != null && isBorrower() && "All Requests".equals(page)) {
                        return "My Requests";
                }
                if ("Pending Requests".equals(page) || "Borrowed Requests".equals(page)) {
                        return "My Requests";
                }
                return page;
        }

        void saveNotificationPreference(boolean enabled) {
                if (user != null && authController != null) {
                        try {
                                authController.updateNotificationPreference(user.getUserId(), enabled);
                        } catch (Exception exception) {
                                throw new RuntimeException(
                                        "Could not save the notification preference.", exception);
                        }
                        user.setNotificationsEnabled(enabled);
                }
                notificationsEnabled = enabled;
        }

        void applyTheme() {
                Color background = AppTheme.background();
                setBackground(background);
                sidebar.setBackground(AppTheme.sidebar());
                sidebar.setBorder(null);
                mainPanel.setBackground(background);
                pageContainer.setBackground(background);
                header.setBackground(background);
                header.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                                javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.border()),
                                new EmptyBorder(14, 30, 14, 28)));
                navigationPanel.setBackground(AppTheme.sidebar());
                pageTitle.setForeground(AppTheme.foreground());
                pageDescription.setForeground(AppTheme.muted());
                if (notificationButton != null) {
                        notificationButton.setForeground(AppTheme.foreground());
                }
                if (headerUserName != null) {
                        headerUserName.setForeground(AppTheme.foreground());
                }
                if (headerUserRole != null) {
                        headerUserRole.setForeground(AppTheme.muted());
                }
                javax.swing.SwingUtilities.updateComponentTreeUI(this);
                revalidate();
                repaint();
        }

        public void setPage(String title, String description, JPanel content) {
                currentPage = title;
                pageTitle.setText(title);
                pageDescription.setText(description);
                setActiveNavigation(navigationItemFor(title));
                pageContainer.removeAll();
                pageContainer.add(new src.ui.components.FadeInPanel(
                                UiSupport.scroll(content)), BorderLayout.CENTER);
                pageContainer.revalidate();
                pageContainer.repaint();
        }

        public void setNavigationAction(String item, Runnable action) {
                NavigationButton button = navigationButtons.get(item);
                if (button != null) {
                        button.addActionListener(event -> action.run());
                }
        }

        public void setActiveNavigation(String activeItem) {
                for (Map.Entry<String, NavigationButton> entry : navigationButtons.entrySet()) {
                        entry.getValue().setActive(entry.getKey().equals(activeItem));
                }
        }

        public void setLogoutAction(Runnable logoutAction) {
                this.logoutAction = logoutAction;
        }

        public JPanel getPageContainer() {
                return pageContainer;
        }

        void updateNotificationButton(int unread) {
                if (notificationButton == null) {
                        return;
                }
                notificationButton.setText(unread > 0
                                ? "Notifications (" + (unread > 99 ? "99+" : unread) + ")"
                                : "Notifications");
                notificationButton.setToolTipText(unread > 0
                                ? unread + " unread notification(s)" : "Open notifications");
        }
}
