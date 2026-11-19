package src.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;

public final class UiSupport {

    /** Client property used to keep one confirmation/action per button at a time. */
    private static final String ACTION_RUNNING = "equiplink.action.running";

    private UiSupport() {
    }

    /**
     * Guards an action button against double-clicks. Returns false when the same
     * button already has a confirmation or background action in progress.
     */
    public static boolean beginAction(AbstractButton button) {
        if (button == null) {
            return true;
        }
        if (Boolean.TRUE.equals(button.getClientProperty(ACTION_RUNNING))) {
            return false;
        }
        button.putClientProperty(ACTION_RUNNING, Boolean.TRUE);
        button.setEnabled(false);
        return true;
    }

    /** Re-enables a guarded button after cancellation, failure, or completion. */
    public static void endAction(AbstractButton button) {
        if (button == null) {
            return;
        }
        button.putClientProperty(ACTION_RUNNING, null);
        SwingUtilities.invokeLater(() -> button.setEnabled(true));
    }

    public static JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(new TopAlignedView(content));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppTheme.background());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    public static void styleInput(JComponent field) {
        field.setFont(AppTheme.regular(13));
        field.setForeground(AppTheme.foreground());
        field.setBackground(AppTheme.surface());
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 40));
    }

    public static DocumentListener documentListener(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                action.run();
            }
        };
    }

    // ---- Centered dialogs -------------------------------------------------------
    // JOptionPane's convenience methods center on their parent component, which can
    // leave a confirmation off-centre when the window is wide. These wrappers always
    // place the dialog in the middle of the screen and keep the same return values as
    // the matching JOptionPane calls, so existing call sites need no other changes.

    public static int showConfirmDialog(
            Component parent, Object message, String title, int optionType, int messageType) {
        return showDialog(parent, title, new JOptionPane(message, messageType, optionType));
    }

    public static void showMessageDialog(
            Component parent, Object message, String title, int messageType) {
        showDialog(parent, title, new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION));
    }

    public static String showInputDialog(
            Component parent, Object message, String title, int messageType) {
        JTextField field = new JTextField(22);
        Object[] prompt = { message, field };
        JOptionPane pane = new JOptionPane(
                prompt, messageType, JOptionPane.OK_CANCEL_OPTION);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        return showDialog(parent, title, pane) == JOptionPane.OK_OPTION
                ? field.getText() : null;
    }

    /**
     * Confirmation shown right after a recoverable delete. Returns 0 when Undo was
     * chosen, 1 when the user dismissed it, and -1 if the dialog was closed.
     */
    public static int showUndoDialog(Component parent, String message, String title) {
        Object[] options = { "Undo", "Dismiss" };
        JOptionPane pane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        Object value = showRawDialog(parent, title, pane);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                return i;
            }
        }
        return JOptionPane.CLOSED_OPTION;
    }

    private static int showDialog(Component parent, String title, JOptionPane pane) {
        Object value = showRawDialog(parent, title, pane);
        return value instanceof Integer ? (Integer) value : JOptionPane.CLOSED_OPTION;
    }

    private static Object showRawDialog(Component parent, String title, JOptionPane pane) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner == null) {
            owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        }
        JDialog dialog;
        if (owner instanceof Dialog) {
            dialog = new JDialog((Dialog) owner, title, true);
        } else if (owner instanceof Frame) {
            dialog = new JDialog((Frame) owner, title, true);
        } else {
            dialog = new JDialog((Frame) null, title, true);
        }
        dialog.setContentPane(pane);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, e -> {
            Object v = pane.getValue();
            if (v != null && v != JOptionPane.UNINITIALIZED_VALUE) {
                dialog.setVisible(false);
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(null); // centre of the screen, not the parent
        dialog.setVisible(true);
        Object value = pane.getValue();
        dialog.dispose();
        return value;
    }

    public static Color statusColor(String status) {
        if (status == null) {
            return AppTheme.muted();
        }
        switch (status) {
            case "RELEASED":
            case "PARTIALLY_RELEASED":
            case "FULLY_RELEASED":
                return AppTheme.primary();
            case "ASSIGNED":
            case "APPROVED":
            case "PARTIALLY_APPROVED":
            case "FULLY_APPROVED":
            case "COMPLETED":
            case "RETURNED":
                return AppTheme.success();
            case "UNABLE":
            case "REJECTED":
            case "DECLINED":
            case "CANCELLED":
            case "NOT_ASSIGNED":
            case "NOT_PROCESSED":
                return AppTheme.danger();
            case "NEEDS_ACTION":
                return AppTheme.warning();
            default:
                return AppTheme.warning();
        }
    }

    public static String displayStatus(String status) {
        if ("RELEASED".equals(status)) {
            return "Borrowed";
        }
        if ("RECEIVED".equals(status)) {
            return "Received";
        }
        if ("PENDING_STAFF".equals(status)) {
            return "Staff review";
        }
        if ("PENDING_ADMIN".equals(status)) {
            return "Awaiting Facility Admin Approval";
        }
        if ("PENDING_DIRECTOR".equals(status)) {
            return "Awaiting Campus Director Approval";
        }
        if ("REJECTED".equals(status)) {
            return "Declined";
        }
        if ("NOT_ASSIGNED".equals(status)) {
            return "Not Assigned";
        }
        if ("NOT_PROCESSED".equals(status)) {
            return "Not Processed on Time";
        }
        if ("UNABLE".equals(status)) {
            return "Unable to fulfill";
        }
        if ("NEEDS_ACTION".equals(status)) {
            return "Needs action";
        }
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        String[] parts = status.toLowerCase().split("_");
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return text.toString();
    }

    /** Label for one department's own status (ASSIGNED reads as "Approved"). */
    public static String departmentStatus(String status) {
        if (status == null) {
            return "Not involved";
        }
        switch (status) {
            case "PENDING":
                return "Pending";
            case "ASSIGNED":
                return "Accepted (Initial review)";
            case "RELEASED":
                return "Released";
            case "RECEIVED":
                return "Received";
            case "RETURNED":
                return "Returned";
            default:
                return displayStatus(status);
        }
    }

    public static java.awt.Color departmentStatusColor(String status) {
        if ("ASSIGNED".equals(status)) {
            return AppTheme.primary(); // not green; distinct from final approval
        }
        if ("RELEASED".equals(status)) {
            return AppTheme.primary();
        }
        return statusColor(status);
    }

    /**
     * Row of badges: overall status, review stage when relevant, and each
     * department's own decision (unless the view is limited to one department).
     */
    public static javax.swing.JPanel statusBadges(
            src.model.BorrowRequest request, String scopeRole, boolean showDepartments) {
        javax.swing.JPanel badges = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        badges.setOpaque(false);
        if (request.isPriority()) {
            badges.add(new src.ui.components.StatusBadge(
                    "Faculty · Priority", AppTheme.isDarkMode()
                            ? new java.awt.Color(196, 181, 253) : new java.awt.Color(124, 58, 237)));
        }
        if (showDepartments) {
            for (String role : new String[] { src.model.RequestStatus.ICT,
                    src.model.RequestStatus.FACILITY }) {
                if (scopeRole != null && !scopeRole.equalsIgnoreCase(role)) {
                    continue;
                }
                String status = src.model.RequestStatus.department(request, role);
                if (status == null) {
                    continue;
                }
                badges.add(new src.ui.components.StatusBadge(
                        src.model.RequestStatus.departmentLabel(role) + ": "
                                + departmentStatus(status),
                        departmentStatusColor(status)));
            }
        }
        String stage = request.getStatus();
        if ("PENDING_ADMIN".equals(stage)) {
            badges.add(new src.ui.components.StatusBadge(
                    "Awaiting Facility Admin Approval", AppTheme.warning()));
        } else if ("PENDING_DIRECTOR".equals(stage)) {
            badges.add(new src.ui.components.StatusBadge(
                    "Awaiting Campus Director Approval", AppTheme.warning()));
        }
        badges.add(new src.ui.components.StatusBadge(request.getOverallStatus(scopeRole)));
        return badges;
    }

    /** "09:00 AM – 10:00 AM" style time range. */
    public static String timeRange(java.time.LocalTime start, java.time.LocalTime end) {
        java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter
                .ofPattern("hh:mm a", java.util.Locale.ENGLISH);
        return start.format(format) + " – " + end.format(format);
    }

    /** Keeps page content at its preferred height and tracks the viewport width. */
    private static final class TopAlignedView extends javax.swing.JPanel
            implements javax.swing.Scrollable {

        TopAlignedView(JComponent content) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            add(content, java.awt.BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
                java.awt.Rectangle visible, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(
                java.awt.Rectangle visible, int orientation, int direction) {
            return visible.height - 36;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getPreferredSize().height < getParent().getHeight();
        }
    }
}
