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
        JScrollPane scroll = new JScrollPane(content);
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
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.border()),
                new EmptyBorder(10, 12, 10, 12)));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 42));
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
                return AppTheme.primary();
            case "COMPLETED":
                return AppTheme.success();
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
        if ("PENDING_STAFF".equals(status)
                || "PENDING_ADMIN".equals(status)
                || "PENDING_DIRECTOR".equals(status)
                || "APPROVED".equals(status)) {
            return "Pending";
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
}
