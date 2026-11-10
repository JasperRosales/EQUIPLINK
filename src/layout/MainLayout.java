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

public class MainLayout extends JPanel {

        private final User user;
        private final AuthController authController;
        private final BorrowRequestController requestController;
        private final EquipmentController equipmentController;
        private final NotificationController notificationController;
        private final JPanel sidebar;
        private JPanel navigationPanel;
        private final JPanel header;
        private final JPanel mainPanel;
        private final JPanel pageContainer;
        private final JLabel pageTitle;
        private final JLabel pageDescription;
        private PrimaryButton notificationButton;
        private JLabel headerUserName;
        private JLabel headerUserRole;
        private final Map<String, NavigationButton> navigationButtons = new LinkedHashMap<>();
        private boolean notificationsEnabled = true;
        private Runnable logoutAction;
        private String currentPage = "Dashboard";

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

        private MainLayout(
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
                header.setBackground(AppTheme.surface());
                header.setBorder(new EmptyBorder(18, 30, 18, 28));
                header.setPreferredSize(new Dimension(0, 94));

                JPanel pageLabels = new JPanel();
                pageLabels.setOpaque(false);
                pageLabels.setLayout(new BoxLayout(pageLabels, BoxLayout.Y_AXIS));
                pageTitle = new JLabel("Dashboard");
                pageTitle.setFont(AppTheme.semibold(19));
                pageTitle.setForeground(AppTheme.foreground());
                pageDescription = new JLabel("");
                pageDescription.setFont(AppTheme.regular(11));
                pageDescription.setForeground(AppTheme.muted());
                pageLabels.add(pageTitle);
                pageLabels.add(Box.createVerticalStrut(4));
                pageLabels.add(pageDescription);
                header.add(pageLabels, BorderLayout.WEST);
                header.add(createHeaderActions(userName, userRole), BorderLayout.EAST);

                pageContainer = new JPanel(new BorderLayout());
                pageContainer.setBackground(AppTheme.background());
                pageContainer.setBorder(new EmptyBorder(22, 28, 22, 28));
                mainPanel.add(header, BorderLayout.NORTH);
                mainPanel.add(pageContainer, BorderLayout.CENTER);

                add(sidebar, BorderLayout.WEST);
                add(mainPanel, BorderLayout.CENTER);
                showPage("Dashboard");
        }

        private JPanel createSidebarTop(String systemName, String[] navigationItems) {
                JPanel top = new JPanel();
                top.setBackground(AppTheme.sidebar());
                top.setBorder(new EmptyBorder(24, 17, 15, 12));
                top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

                JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 11, 0));
                brand.setOpaque(false);
                brand.setAlignmentX(LEFT_ALIGNMENT);
                JLabel logo = new JLabel("EL", SwingConstants.CENTER);
                logo.setPreferredSize(new Dimension(38, 38));
                logo.setFont(AppTheme.semibold(12));
                logo.setForeground(Color.WHITE);
                logo.setOpaque(true);
                logo.setBackground(AppTheme.primary());
                logo.setBorder(new EmptyBorder(6, 8, 6, 8));
                logo.setHorizontalAlignment(SwingConstants.CENTER);
                JLabel name = new JLabel(systemName);
                name.setFont(AppTheme.semibold(17));
                name.setForeground(Color.WHITE);
                brand.add(logo);
                brand.add(name);
                top.add(brand);
                top.add(Box.createVerticalStrut(32));

                JLabel menuLabel = new JLabel("WORKSPACE");
                menuLabel.setFont(AppTheme.semibold(9));
                menuLabel.setForeground(new Color(136, 153, 179));
                menuLabel.setBorder(new EmptyBorder(0, 3, 10, 0));
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

        private JPanel createProfile(String name, String role) {
                JPanel profile = new JPanel(new BorderLayout(10, 0));
                profile.setBackground(new Color(17, 26, 45));
                profile.setBorder(new EmptyBorder(14, 17, 15, 17));
                JLabel initials = new JLabel(initials(name), SwingConstants.CENTER);
                initials.setFont(AppTheme.semibold(11));
                initials.setForeground(Color.WHITE);
                initials.setOpaque(true);
                initials.setBackground(new Color(65, 87, 124));
                initials.setPreferredSize(new Dimension(38, 38));

                JPanel identity = new JPanel();
                identity.setOpaque(false);
                identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
                JLabel profileName = new JLabel(name);
                profileName.setFont(AppTheme.semibold(10));
                profileName.setForeground(Color.WHITE);
                JLabel profileRole = new JLabel(role);
                profileRole.setFont(AppTheme.regular(9));
                profileRole.setForeground(new Color(160, 176, 199));
                identity.add(profileName);
                identity.add(Box.createVerticalStrut(3));
                identity.add(profileRole);
                profile.add(initials, BorderLayout.WEST);
                profile.add(identity, BorderLayout.CENTER);

                JButton logout = new JButton("Log out");
                logout.setToolTipText("Log out");
                logout.setFont(AppTheme.regular(8));
                logout.setForeground(new Color(195, 207, 225));
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

        private JPanel createHeaderActions(String name, String role) {
                JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
                actions.setOpaque(false);
                notificationButton = new PrimaryButton("Notifications",
                                PrimaryButton.Style.SECONDARY);
                notificationButton.setToolTipText("Open notifications");
                notificationButton.addActionListener(event -> showPage("Notifications"));
                actions.add(notificationButton);

                JPanel roleBadge = new JPanel(new GridLayout(0, 1, 0, 2));
                roleBadge.setOpaque(false);
                headerUserName = new JLabel(name);
                headerUserName.setFont(AppTheme.semibold(10));
                headerUserName.setForeground(AppTheme.foreground());
                headerUserRole = new JLabel(role);
                headerUserRole.setFont(AppTheme.regular(9));
                headerUserRole.setForeground(AppTheme.muted());
                roleBadge.add(headerUserName);
                roleBadge.add(headerUserRole);
                actions.add(roleBadge);
                return actions;
        }

        public static String[] navigationFor(String role) {
                switch (normalizeRole(role)) {
                        case "BORROWER":
                                return new String[] {
                                                "Dashboard", "Request Equipment", "My Requests",
                                                "Borrowing Request", "Notifications", "Settings"
                                };
                        case "ICT_STAFF":
                                return new String[] {
                                                "Dashboard", "Equipment", "Requests",
                                                "Schedule & Assignment", "Transaction History",
                                                "Password Requests", "User Management",
                                                "All Requests", "Notifications", "Settings"
                                };
                        case "FACILITY_STAFF":
                                return new String[] {
                                                "Dashboard", "Equipment", "Requests",
                                                "Schedule & Assignment", "Transaction History",
                                                "Notifications", "Settings"
                                };
                        case "DIRECTOR":
                                return new String[] {
                                                "Dashboard", "Request Approval", "All Requests",
                                                "Notifications", "Settings"
                                };
                        case "ADMIN":
                        default:
                                return new String[] {
                                                "Dashboard", "Borrowing Requests", "All Requests",
                                                "Notifications", "Settings"
                                };
                }
        }

        private static String normalizeRole(String role) {
                return role == null ? "" : role.trim().toUpperCase().replace(' ', '_');
        }

        private static String displayRole(String role) {
                switch (normalizeRole(role)) {
                        case "BORROWER":
                                return "User";
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

        private String initials(String name) {
                if (name == null || name.isBlank()) {
                        return "EL";
                }
                String[] parts = name.trim().split("\\s+");
                return parts.length == 1
                                ? parts[0].substring(0, 1).toUpperCase()
                                : (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                                                .toUpperCase();
        }

        public void showPage(String item) {
                currentPage = item;
                String title = pageTitleFor(item);
                pageTitle.setText(title);
                pageDescription.setText(pageDescriptionFor(item));
                setActiveNavigation(navigationItemFor(item));
                pageContainer.removeAll();
                pageContainer.add(UiSupport.scroll(createPage(item)), BorderLayout.CENTER);
                pageContainer.revalidate();
                pageContainer.repaint();
        }

        private javax.swing.JComponent createPage(String item) {
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
                                                        this::refreshCurrentPage);
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
                                                enabled -> notificationsEnabled = enabled);
                        case "Notifications":
                                return new NotificationsPanel(user.getUserId(), notificationController);
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
                                if (isStaff()) {
                                        return new RequestQueuePanel(
                                                        user, requestController,
                                                        RequestQueuePanel.Mode.TRANSACTION_HISTORY);
                                }
                                return infoPage(item, "This page is available to equipment staff.");
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
                                return new RequestQueuePanel(
                                                user, requestController,
                                                RequestQueuePanel.Mode.ALL_REQUESTS);
                        case "Password Requests":
                                if ("ICT_STAFF".equals(normalizeRole(user.getRole()))) {
                                        return new PasswordResetRequestsPanel(user, authController);
                                }
                                return infoPage(item, "This page is available to ICT system administrators.");
                        case "User Management":
                                if ("ICT_STAFF".equals(normalizeRole(user.getRole()))) {
                                        return new UserManagementPanel(user, authController);
                                }
                                return infoPage(item, "This page is available to ICT system administrators.");
                        default:
                                return infoPage(item,
                                                "This workspace is ready for " + item.toLowerCase()
                                                                + " tools and records.");
                }
        }

        private EquipmentRequestPanel requestForm(BorrowRequest request) {
                return new EquipmentRequestPanel(
                                user, requestController, equipmentController, request,
                                () -> showPage("My Requests"));
        }

        private void editRequest(BorrowRequest request) {
                currentPage = "My Requests";
                pageTitle.setText("Edit request");
                pageDescription.setText("Update details for " + request.getRequestCode());
                setActiveNavigation("My Requests");
                pageContainer.removeAll();
                pageContainer.add(UiSupport.scroll(requestForm(request)), BorderLayout.CENTER);
                pageContainer.revalidate();
                pageContainer.repaint();
        }

        private javax.swing.JComponent infoPage(String title, String detail) {
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

        private String pageTitleFor(String item) {
                if ("Borrowing Request".equals(item)) {
                        return "Borrowing History";
                }
                if ("Pending Requests".equals(item) || "Borrowed Requests".equals(item)) {
                        return "My Requests";
                }
                return item;
        }

        private String pageDescriptionFor(String item) {
                switch (item) {
                        case "Dashboard":
                                return "";
                        case "Request Equipment":
                                return "Create a request using live campus inventory";
                        case "My Requests":
                                return "Track pending requests and equipment you have borrowed";
                        case "Pending Requests":
                                return "Requests waiting for review or action";
                        case "Borrowed Requests":
                                return "Equipment currently released to you";
                        case "Borrowing Request":
                                return "Your completed, cancelled, and declined requests";
                        case "Settings":
                                return "Appearance, notifications, and account security";
                        case "Notifications":
                                return "Stay up to date on request activity";
                        default:
                                return "Manage " + item.toLowerCase() + " in EquipLink";
                }
        }

        private boolean isBorrower() {
                return "BORROWER".equals(normalizeRole(user.getRole()));
        }

        private boolean isStaff() {
                String role = normalizeRole(user.getRole());
                return "ICT_STAFF".equals(role) || "FACILITY_STAFF".equals(role);
        }

        private String navigationItemFor(String page) {
                if ("Pending Requests".equals(page) || "Borrowed Requests".equals(page)) {
                        return "My Requests";
                }
                return page;
        }

        private void refreshCurrentPage() {
                applyTheme();
                showPage(currentPage);
        }

        private void applyTheme() {
                Color background = AppTheme.background();
                Color surface = AppTheme.surface();
                setBackground(background);
                mainPanel.setBackground(background);
                pageContainer.setBackground(background);
                header.setBackground(surface);
                navigationPanel.setBackground(AppTheme.sidebar());
                pageTitle.setForeground(AppTheme.foreground());
                pageDescription.setForeground(AppTheme.muted());
                notificationButton.setForeground(AppTheme.foreground());
                headerUserName.setForeground(AppTheme.foreground());
                headerUserRole.setForeground(AppTheme.muted());
                revalidate();
                repaint();
        }

        public void setPage(String title, String description, JPanel content) {
                currentPage = title;
                pageTitle.setText(title);
                pageDescription.setText(description);
                setActiveNavigation(navigationItemFor(title));
                pageContainer.removeAll();
                pageContainer.add(UiSupport.scroll(content), BorderLayout.CENTER);
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
}
