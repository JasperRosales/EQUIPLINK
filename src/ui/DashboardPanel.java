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

public class DashboardPanel extends DashboardPanelPart1 implements Refreshable {

    public DashboardPanel(
            User user,
            BorrowRequestController requestController,
            EquipmentController equipmentController,
            Consumer<String> openPage) {
        this.user = user;
        this.requestController = requestController;
        this.equipmentController = equipmentController;
        this.openPage = openPage;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        addWelcomeCard();
        add(Box.createVerticalStrut(18));
        addStatCards();
        add(Box.createVerticalStrut(18));
        addBottomContent();
        loadDashboardData();
    }

    void loadDashboardData() {
        loadDashboardCounts();
        loadRequestPreview();
    }

    @Override
    public void reloadData() {
        loadDashboardData();
    }

    void loadRequestPreview() {
        new SwingWorker<List<BorrowRequest>, Void>() {
            @Override
            protected List<BorrowRequest> doInBackground() throws Exception {
                if (isBorrower()) {
                    return requestController.findActiveRequests(user.getUserId());
                }
                if (isStaff()) {
                    return requestController.findStaffQueue(user);
                }
                if (isDirector()) {
                    return requestController.findDirectorQueue(user);
                }
                return requestController.findAdminQueue(user);
            }

            @Override
            protected void done() {
                try {
                    showRecentRequests(get());
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    showError("Unable to load recent requests: " + cause.getMessage());
                }
            }
        }.execute();
    }

    void showRecentRequests(List<BorrowRequest> requests) {
        requestList.removeAll();
        if (requests.isEmpty()) {
            JLabel empty = new JLabel(isBorrower()
                    ? "Your recent requests will appear here."
                    : "No requests are waiting for action.");
            empty.setFont(AppTheme.regular(11));
            empty.setForeground(AppTheme.muted());
            requestList.add(empty);
        } else {
            int visibleCount = isDirector() ? requests.size() : Math.min(3, requests.size());
            for (BorrowRequest record : requests.subList(0, visibleCount)) {
                JPanel row = new JPanel(new BorderLayout(8, 4));
                row.setOpaque(false);
                row.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                        javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.border()),
                        new EmptyBorder(9, 0, 9, 0)));
                JLabel heading = new JLabel(record.getRequestCode());
                heading.setFont(AppTheme.semibold(12.5f));
                heading.setForeground(AppTheme.foreground());
                JLabel details = new JLabel(
                        record.getVenue() + "  ·  "
                                + record.getEventDate().format(
                                        DateTimeFormatter.ofPattern("MMM d, yyyy")));
                details.setFont(AppTheme.regular(11));
                details.setForeground(AppTheme.muted());
                row.add(heading, BorderLayout.NORTH);
                row.add(details, BorderLayout.CENTER);
                JLabel status = new src.ui.components.StatusBadge(
                        record.getOverallStatus(isStaff() ? user.getRole().toUpperCase() : null));
                row.add(status, BorderLayout.EAST);
                requestList.add(row);
            }
        }
        requestList.revalidate();
        requestList.repaint();
    }
}
