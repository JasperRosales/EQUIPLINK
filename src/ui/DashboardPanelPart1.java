package src.ui;

import src.model.BorrowRequest;
import src.model.RequestStatus;
import src.model.RequestStatus.Category;
import src.model.User;
import src.controller.BorrowRequestController;
import src.controller.EquipmentController;
import src.ui.components.DashboardCard;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/** Part 1 of DashboardPanel, split out to keep files short. */
public abstract class DashboardPanelPart1 extends JPanel {

    static final Color GREEN = AppTheme.success();
    static final Color PURPLE = AppTheme.accent();
    static final Color ORANGE = AppTheme.warning();

    User user;
    BorrowRequestController requestController;
    EquipmentController equipmentController;
    Consumer<String> openPage;
    final JPanel requestList = new JPanel();
    final DashboardCard[] statCards = new DashboardCard[8];

    void addWelcomeCard() {
        SurfacePanel welcome = new SurfacePanel();
        welcome.setLayout(new BorderLayout(15, 5));
        welcome.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        welcome.setAlignmentX(LEFT_ALIGNMENT);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        String fullName = user.getFullName() == null ? "" : user.getFullName().trim();
        String firstName = fullName.isEmpty() ? "there" : fullName.split("\\s+")[0];
        JLabel greeting = new JLabel("Good day, " + firstName + "!");
        greeting.setFont(AppTheme.semibold(19));
        greeting.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel(isBorrower()
                ? "Welcome back to your campus equipment workspace."
                : "Review request activity and manage campus equipment.");
        subtitle.setFont(AppTheme.regular(12));
        subtitle.setForeground(AppTheme.muted());
        text.add(greeting);
        text.add(Box.createVerticalStrut(3));
        text.add(subtitle);
        welcome.add(text, BorderLayout.CENTER);

        PrimaryButton action = new PrimaryButton(isBorrower()
                ? "Request equipment"
                : "Open request queue");

        JPanel actionWrap = new JPanel(new java.awt.GridBagLayout());
        actionWrap.setOpaque(false);
        actionWrap.add(action);

        action.addActionListener(event -> openPage.accept(
                isBorrower() ? "Request Equipment" : "Borrowing Requests"));
        welcome.add(actionWrap, BorderLayout.EAST);
        add(welcome);
    }

    void addStatCards() {
        String all = isBorrower() ? "My Requests::"
                : isStaff() || isDirector() ? "Transaction History::" : "All Requests::";
        if (isStaff()) {
            statCards[0] = new DashboardCard("Total equipment", "—",
                    "In your inventory", AppTheme.primary());
            statCards[1] = new DashboardCard("Available", "—",
                    "Ready to assign", GREEN);
            statCards[2] = new DashboardCard("Reserved today", "—",
                    "Scheduled for today", PURPLE);
            statCards[3] = new DashboardCard("In use", "—",
                    "Released today", ORANGE);
            statCards[0].setClickAction(() -> openPage.accept("Inventory"));
            statCards[1].setClickAction(() -> openPage.accept("Inventory"));
            statCards[2].setClickAction(() -> openPage.accept("Schedule & Assignment"));
            statCards[3].setClickAction(() -> openPage.accept("Schedule & Assignment"));
            addCardRow(0, 4);
            add(Box.createVerticalStrut(14));
            String department = RequestStatus.departmentLabel(user.getRole());
            statCards[4] = new DashboardCard("Awaiting Decision", "—",
                    department + " equipment pending", ORANGE);
            statCards[5] = new DashboardCard("Approved", "—",
                    "Approved by " + department, GREEN);
            statCards[6] = new DashboardCard("Declined", "—",
                    "Declined requests", AppTheme.danger());
            statCards[7] = new DashboardCard("Overdue", "—",
                    "Not processed on time", AppTheme.danger());
            statCards[4].setClickAction(() -> openPage.accept(all + Category.PENDING.getLabel()));
            statCards[5].setClickAction(() -> openPage.accept(all + Category.APPROVED.getLabel()));
            statCards[6].setClickAction(() -> openPage.accept(all + Category.DECLINED.getLabel()));
            statCards[7].setClickAction(() -> openPage.accept(all + Category.OVERDUE.getLabel()));
            addCardRow(4, 4);
            return;
        }
        if (isBorrower()) {
            statCards[0] = new DashboardCard("My requests", "—",
                    "Active requests", AppTheme.primary());
            statCards[1] = new DashboardCard("Pending", "—",
                    "Awaiting department review", ORANGE);
            statCards[2] = new DashboardCard("Approved", "—",
                    "Partially or fully approved", GREEN);
            statCards[3] = new DashboardCard("Released", "—",
                    "Equipment released to you", PURPLE);
            statCards[0].setClickAction(() -> openPage.accept("My Requests"));
            statCards[1].setClickAction(() -> openPage.accept(all + Category.PENDING.getLabel()));
            statCards[2].setClickAction(() -> openPage.accept(all + Category.APPROVED.getLabel()));
            statCards[3].setClickAction(() -> openPage.accept("Borrowed Requests"));
        } else {
            boolean director = isDirector();
            statCards[0] = new DashboardCard("Pending review", "—",
                    director ? "Awaiting your decision" : "Department-processed requests",
                    director ? ORANGE : AppTheme.primary());
            statCards[1] = new DashboardCard("Approved requests", "—",
                    "Final approval granted", GREEN);
            statCards[2] = new DashboardCard("Declined requests", "—",
                    "Closed after review", AppTheme.danger());
            statCards[3] = new DashboardCard("Overdue", "—",
                    "Not processed on time", ORANGE);
            statCards[0].setClickAction(() -> openPage.accept(
                    director ? "Request Approval" : "Borrowing Requests"));
            statCards[1].setClickAction(() -> openPage.accept(all + Category.FINAL_APPROVED.getLabel()));
            statCards[2].setClickAction(() -> openPage.accept(all + Category.DECLINED.getLabel()));
            statCards[3].setClickAction(() -> openPage.accept(all + Category.OVERDUE.getLabel()));
        }
        addCardRow(0, 4);
    }

    void addCardRow(int from, int count) {
        JPanel cards = new JPanel(new GridLayout(1, count, 14, 0));
        cards.setOpaque(false);
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
        cards.setAlignmentX(LEFT_ALIGNMENT);
        for (int i = from; i < from + count; i++) {
            cards.add(statCards[i]);
        }
        add(cards);
    }

    void addBottomContent() {
        JPanel bottom = new JPanel(new GridLayout(1, 2, 16, 0));
        bottom.setOpaque(false);
        bottom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 285));

        SurfacePanel recent = new SurfacePanel();
        recent.setLayout(new BorderLayout(0, 12));
        JPanel recentHeading = new JPanel(new BorderLayout());
        recentHeading.setOpaque(false);
        JLabel title = new JLabel(isBorrower() ? "Recent requests"
                : isDirector() ? "Pending Approval"
                        : isStaff() ? "Requests to process" : "Pending requests");
        title.setFont(AppTheme.semibold(15));
        title.setForeground(AppTheme.foreground());
        PrimaryButton viewAll = new PrimaryButton(
                isBorrower() ? "View all" : isDirector() ? "Review all"
                        : isStaff() ? "Open requests" : "Open queue",
                PrimaryButton.Style.SECONDARY);
        viewAll.addActionListener(event -> openPage.accept(
                isBorrower() ? "My Requests"
                        : isDirector() ? "Request Approval"
                                : isStaff() ? "Requests" : "Borrowing Requests"));
        recentHeading.add(title, BorderLayout.WEST);
        recentHeading.add(viewAll, BorderLayout.EAST);
        recent.add(recentHeading, BorderLayout.NORTH);

        requestList.setOpaque(false);
        requestList.setLayout(new BoxLayout(requestList, BoxLayout.Y_AXIS));
        JScrollPane recentScroll = UiSupport.scroll(requestList);
        recentScroll.setPreferredSize(new Dimension(0, 195));
        recentScroll.setOpaque(false);
        recentScroll.getViewport().setOpaque(false);
        recent.add(recentScroll, BorderLayout.CENTER);

        SurfacePanel shortcuts = new SurfacePanel();
        shortcuts.setLayout(new BoxLayout(shortcuts, BoxLayout.Y_AXIS));
        JLabel shortcutsTitle = new JLabel("Quick actions");
        shortcutsTitle.setFont(AppTheme.semibold(15));
        shortcutsTitle.setForeground(AppTheme.foreground());
        shortcuts.add(shortcutsTitle);
        shortcuts.add(Box.createVerticalStrut(14));
        addShortcut(shortcuts,
                isBorrower() ? "Request equipment"
                        : isDirector() ? "Request approval"
                                : isStaff() ? "Requests" : "Borrowing requests",
                isBorrower() ? "Browse inventory and create a request."
                        : isDirector() ? "Review requests requiring final approval."
                                : isStaff() ? "Check and assign requests in your queue."
                                        : "Review staff-assigned requests.",
                isBorrower() ? "Request Equipment"
                        : isDirector() ? "Request Approval"
                                : isStaff() ? "Requests" : "Borrowing Requests");
        shortcuts.add(Box.createVerticalStrut(9));
        addShortcut(shortcuts,
                isBorrower() ? "Borrowing history"
                        : isDirector() ? "Transaction history"
                                : isStaff() ? "Schedule & assignment" : "All requests",
                isBorrower() ? "See your completed and past requests."
                        : isDirector() ? "Search the complete transaction history."
                                : isStaff() ? "Release and track approved equipment."
                                        : "Search all request records.",
                isBorrower() ? "Borrowing Request"
                        : isDirector() ? "Transaction History"
                                : isStaff() ? "Schedule & Assignment" : "All Requests");
        bottom.add(recent);
        bottom.add(shortcuts);
        add(bottom);
    }

    void addShortcut(JPanel parent, String title, String subtitle, String page) {
        PrimaryButton shortcut = new PrimaryButton(title, PrimaryButton.Style.SECONDARY);
        shortcut.setHorizontalAlignment(SwingConstants.LEFT);
        shortcut.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        shortcut.addActionListener(event -> openPage.accept(page));
        JLabel note = new JLabel(subtitle);
        note.setFont(AppTheme.regular(10));
        note.setForeground(AppTheme.muted());
        parent.add(shortcut);
        parent.add(note);
    }

    /**
     * Request counters are computed from today's activity and the same status
     * categories the filtered pages use, so a card always opens its matching status.
     */
    void loadDashboardCounts() {
        new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() throws Exception {
                // Only today's activity is counted. Historical requests stay in the
                // database and in Transaction History; a new day starts at zero here.
                List<BorrowRequest> requests = requestController.findDailyActivity(
                        user, src.ui.components.DateFilter.today());
                if (isBorrower()) {
                    int active = 0;
                    for (BorrowRequest request : requests) {
                        String status = request.getStatus();
                        if (!"COMPLETED".equals(status) && !"REJECTED".equals(status)
                                && !"CANCELLED".equals(status)) {
                            active++;
                        }
                    }
                    return new int[] {
                            active,
                            count(Category.PENDING, requests, null),
                            count(Category.APPROVED, requests, null),
                            count(Category.PARTIALLY_RELEASED, requests, null)
                                    + count(Category.FULLY_RELEASED, requests, null)
                    };
                }
                if (isStaff()) {
                    String role = user.getRole().toUpperCase();
                    return new int[] {
                            equipmentController.countTotalUnitsForRole(user.getRole()),
                            equipmentController.countAvailableUnitsForRole(user.getRole()),
                            equipmentController.countReservedUnitsToday(user.getRole()),
                            equipmentController.countInUseUnitsToday(user.getRole()),
                            count(Category.PENDING, requests, role),
                            count(Category.APPROVED, requests, role),
                            count(Category.DECLINED, requests, role),
                            count(Category.OVERDUE, requests, role)
                    };
                }
                return new int[] {
                        count(isDirector() ? Category.DIRECTOR_REVIEW : Category.ADMIN_REVIEW,
                                requests, null),
                        count(Category.FINAL_APPROVED, requests, null),
                        count(Category.DECLINED, requests, null),
                        count(Category.OVERDUE, requests, null)
                };
            }

            @Override
            protected void done() {
                try {
                    int[] counts = get();
                    for (int i = 0; i < counts.length; i++) {
                        setCardValue(i, counts[i]);
                    }
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception
                            : exception.getCause();
                    showError("Unable to load dashboard summary: " + cause.getMessage());
                }
            }
        }.execute();
    }

    static int count(Category category, List<BorrowRequest> requests, String scope) {
        return (int) category.count(requests, scope);
    }

    void setCardValue(int index, int count) {
        statCards[index].setValue(Integer.toString(count));
        statCards[index].revalidate();
        statCards[index].repaint();
    }

    void showError(String message) {
        JLabel error = new JLabel(message);
        error.setFont(AppTheme.regular(10));
        error.setForeground(AppTheme.danger());
        error.setBorder(new EmptyBorder(6, 0, 0, 0));
        requestList.add(error);
        requestList.revalidate();
        requestList.repaint();
    }

    boolean isBorrower() {
        return "BORROWER".equalsIgnoreCase(user.getRole());
    }

    boolean isStaff() {
        return isIctStaff() || "FACILITY_STAFF".equalsIgnoreCase(user.getRole());
    }

    boolean isIctStaff() {
        return "ICT_STAFF".equalsIgnoreCase(user.getRole());
    }

    boolean isDirector() {
        return "DIRECTOR".equalsIgnoreCase(user.getRole());
    }
}
