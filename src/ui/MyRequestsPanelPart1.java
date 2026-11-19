package src.ui;

import src.model.BorrowRequest;
import src.controller.BorrowRequestController;
import src.util.UndoPolicy;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Part 1 of MyRequestsPanel, split out to keep files short. */
public abstract class MyRequestsPanelPart1 extends JPanel {

    long borrowerId;
    BorrowRequestController requestController;
    Consumer<BorrowRequest> editHandler;
    Runnable changedHandler;
    final JPanel requestCards = new JPanel();
    final JTextField search = new JTextField(22);
    final src.ui.components.DateFilter dateFilter = new src.ui.components.DateFilter();
    final JComboBox<String> dateSort = new JComboBox<>(
            new String[] { "Newest first", "Oldest first" });
    String statusFilter;
    JComboBox<String> categoryFilter;
    final JLabel resultCount = new JLabel(" ");
    List<BorrowRequest> requests = List.of();
    boolean loading;

    static final String ALL_REQUESTS = "All requests";
    static final String ALL_ACTIVE = "Active requests";
    static final String RELEASED = "Released";

    boolean matchesFilter(BorrowRequest request) {
        String selected = String.valueOf(categoryFilter.getSelectedItem());
        if (ALL_REQUESTS.equals(selected)) {
            return true;
        }
        if (ALL_ACTIVE.equals(selected)) {
            return isActive(request);
        }
        if (RELEASED.equals(selected)) {
            return src.model.RequestStatus.Category.FULLY_RELEASED.matches(request, null)
                    || src.model.RequestStatus.Category.PARTIALLY_RELEASED.matches(request, null);
        }
        return src.model.RequestStatus.Category.fromLabel(selected).matches(request, null);
    }

    static boolean isActive(BorrowRequest request) {
        String status = request.getStatus();
        return !"COMPLETED".equals(status) && !"REJECTED".equals(status)
                && !"CANCELLED".equals(status);
    }

    static boolean matchesDate(src.ui.components.DateFilter filter, BorrowRequest request) {
        if (filter.matches(request.getEventDate())) {
            return true;
        }
        for (src.model.RequestOccurrence occurrence : request.getOccurrences()) {
            if (filter.matches(occurrence.getEventDate())) {
                return true;
            }
        }
        return false;
    }

    /** Dates with at least one request, so both calendars use the same blue highlight. */
    java.util.Set<java.time.LocalDate> buildRequestDates() {
        java.util.Set<java.time.LocalDate> days = new java.util.HashSet<>();
        for (BorrowRequest request : requests) {
            if (request.getEventDate() != null) {
                days.add(request.getEventDate());
            }
            for (src.model.RequestOccurrence occurrence : request.getOccurrences()) {
                if (occurrence.getEventDate() != null) {
                    days.add(occurrence.getEventDate());
                }
            }
        }
        return days;
    }

    String searchableText(BorrowRequest request) {
        StringBuilder text = new StringBuilder()
                .append(request.getRequestCode()).append(' ')
                .append(request.getVenue()).append(' ')
                .append(request.getPurpose()).append(' ')
                .append(request.getStatus()).append(' ')
                .append(UiSupport.displayStatus(request.getOverallStatus()));
        for (var item : request.getEquipmentItems()) {
            text.append(' ').append(item.getEquipmentName())
                    .append(' ').append(item.getEquipmentCode());
        }
        return text.toString().toLowerCase();
    }

    JPanel requestCard(BorrowRequest request) {
        final float[] hover = { 0f };
        SurfacePanel card = new SurfacePanel() {
            @Override
            protected void paintBorder(java.awt.Graphics graphics) {
                super.paintBorder(graphics);
                if (hover[0] <= 0f)
                    return;
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.alpha(AppTheme.PRIMARY, Math.round(255 * hover[0])));
                g.setStroke(new java.awt.BasicStroke(2f));
                int m = SurfacePanel.MARGIN;
                g.drawRoundRect(m, m, getWidth() - m * 2 - 1, getHeight() - m * 2 - 1, 18, 18);
                g.dispose();
            }
        };
        javax.swing.Timer hoverTimer = new javax.swing.Timer(15, null);
        hoverTimer.addActionListener(event -> {
            boolean inside = card.getMousePosition() != null;
            float target = inside ? 1f : 0f, step = 15f / 200f;
            if (Math.abs(hover[0] - target) <= step) {
                hover[0] = target;
                hoverTimer.stop();
            } else {
                hover[0] += hover[0] < target ? step : -step;
            }
            card.repaint();
        });
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (!hoverTimer.isRunning())
                    hoverTimer.start();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (!hoverTimer.isRunning())
                    hoverTimer.start();
            }
        });
        card.setLayout(new BorderLayout(15, 7));
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new BorderLayout(10, 0));
        titleRow.setOpaque(false);
        JLabel code = new JLabel(request.getRequestCode());
        code.setFont(AppTheme.semibold(14));
        code.setForeground(AppTheme.foreground());
        titleRow.add(code, BorderLayout.WEST);
        titleRow.add(UiSupport.statusBadges(request, null, true), BorderLayout.EAST);

        String itemSummary = request.getEquipmentItems().isEmpty()
                ? "No equipment items"
                : request.getEquipmentItems().get(0).getEquipmentName()
                        + (request.getEquipmentItems().size() > 1
                                ? " +" + (request.getEquipmentItems().size() - 1) + " more"
                                : "");
        JLabel subtitle = new JLabel(itemSummary + "  ·  " + request.getVenue()
                + "  ·  " + request.getEventDate().format(
                        DateTimeFormatter.ofPattern("MMM d, yyyy")));
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        details.add(titleRow);
        details.add(Box.createVerticalStrut(7));
        details.add(subtitle);
        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        PrimaryButton view = new PrimaryButton("View details", PrimaryButton.Style.SECONDARY);
        view.addActionListener(event -> showDetails(request));
        actions.add(view);

        if (isEditable(request)) {
            PrimaryButton edit = new PrimaryButton("Edit", PrimaryButton.Style.SECONDARY);
            edit.addActionListener(event -> editHandler.accept(request));
            actions.add(edit);
        }
        if (canReceive(request)) {
            PrimaryButton receive = new PrimaryButton("Receive");
            receive.addActionListener(event -> receiveRequest(request));
            actions.add(receive);
        }
        // Every request can be removed by its owner, including completed ones.
        PrimaryButton delete = new PrimaryButton("Delete", PrimaryButton.Style.DANGER);
        delete.setToolTipText("Delete this request (undo available for "
                + src.util.UndoPolicy.RECOVERY_LABEL + ")");
        delete.addActionListener(event -> deleteRequest(request, delete));
        actions.add(delete);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    boolean isEditable(BorrowRequest request) {
        return "PENDING_STAFF".equals(request.getStatus())
                || "NEEDS_ACTION".equals(request.getStatus());
    }

    boolean canReceive(BorrowRequest request) {
        if (request == null) return false;
        if (!src.model.RequestStatus.isFinalApprovalGranted(request)) return false;
        for (src.model.DepartmentStatus department : request.getDepartments()) {
            if ("RELEASED".equalsIgnoreCase(department.getStatus())) return true;
        }
        return false;
    }

    void showDetails(BorrowRequest request) {
        Window owner = javax.swing.SwingUtilities.getWindowAncestor(this);
        new RequestDetailsDialog(owner, request).setVisible(true);
    }

    void receiveRequest(BorrowRequest request) {
        int ans = UiSupport.showConfirmDialog(this,
                "Confirm you have received the released equipment for request " + request.getRequestCode() + "?",
                "Confirm receipt", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans != JOptionPane.YES_OPTION) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                src.model.User actor = new src.model.User(borrowerId, "", "", "", "", "BORROWER", "", true);
                requestController.receiveByBorrower(actor, request.getRequestId());
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    changedHandler.run();
                    UiSupport.showMessageDialog(MyRequestsPanelPart1.this, "Receipt confirmed. Staff has been notified.", "Received", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() == null ? ex : ex.getCause();
                    UiSupport.showMessageDialog(MyRequestsPanelPart1.this, c.getMessage(), "Unable to confirm receipt", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void deleteRequest(BorrowRequest request, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        long requestId = request.getRequestId();
        String code = request.getRequestCode();
        int answer = UiSupport.showConfirmDialog(
                this,
                "Delete " + code + "? It will be removed from your requests.\n"
                        + "You can undo this for " + UndoPolicy.RECOVERY_LABEL + ".",
                "Delete request",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            UiSupport.endAction(source);
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                requestController.deleteRequest(borrowerId, requestId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    changedHandler.run();
                    offerUndo(code, requestId);
                } catch (Exception exception) {
                    String message = exception.getCause() == null
                            ? exception.getMessage()
                            : exception.getCause().getMessage();
                    UiSupport.showMessageDialog(
                            MyRequestsPanelPart1.this, message, "Unable to delete request",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }

    /** Shows the recovery prompt immediately after a successful delete. */
    void offerUndo(String code, long requestId) {
        int choice = UiSupport.showUndoDialog(this,
                code + " was deleted.\nUndo is available for " + UndoPolicy.RECOVERY_LABEL + ".",
                "Request deleted");
        if (choice != 0) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                requestController.restoreRequest(borrowerId, requestId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    changedHandler.run();
                    UiSupport.showMessageDialog(MyRequestsPanelPart1.this,
                            code + " was restored with its original status.",
                            "Request restored", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    UiSupport.showMessageDialog(MyRequestsPanelPart1.this,
                            cause.getMessage(), "Unable to restore request",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    JPanel messageCard(String text, boolean error) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(12));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        card.add(label, BorderLayout.CENTER);
        return card;
    }
}
