package src.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Dimension;

public final class UiSupport {

    private UiSupport() {
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

    public static Color statusColor(String status) {
        if (status == null) {
            return AppTheme.muted();
        }
        switch (status) {
            case "RELEASED":
            case "ASSIGNED":
                return AppTheme.primary();
            case "APPROVED":
            case "COMPLETED":
            case "RETURNED":
                return AppTheme.success();
            case "UNABLE":
            case "REJECTED":
            case "CANCELLED":
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
        if ("PENDING_STAFF".equals(status)) {
            return "Staff review";
        }
        if ("PENDING_ADMIN".equals(status)) {
            return "Admin review";
        }
        if ("PENDING_DIRECTOR".equals(status)) {
            return "Director review";
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
