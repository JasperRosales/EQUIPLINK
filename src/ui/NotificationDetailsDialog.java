package src.ui;

import src.model.Notification;
import src.model.RequestStatus;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Full view of one notification with shortcuts to the request and to delete it. */
public class NotificationDetailsDialog extends JDialog {

    public NotificationDetailsDialog(
            Window owner, Notification notification, Runnable viewRequest, Runnable delete) {
        super(owner, "Notification details", Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(AppTheme.background());
        content.setBorder(new EmptyBorder(18, 20, 14, 20));

        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout(0, 12));
        JLabel title = new JLabel(notification.getTitle());
        title.setFont(AppTheme.semibold(16));
        title.setForeground(AppTheme.foreground());
        card.add(title, BorderLayout.NORTH);

        JTextArea message = new JTextArea(notification.getMessage());
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setEditable(false);
        message.setOpaque(false);
        message.setBorder(null);
        message.setFont(AppTheme.regular(12.5f));
        message.setForeground(AppTheme.foreground());
        message.setRows(3);

        JPanel facts = new JPanel(new GridBagLayout());
        facts.setOpaque(false);
        int row = 0;
        String created = notification.getCreatedAt() == null ? "—"
                : notification.getCreatedAt().format(DateTimeFormatter.ofPattern(
                        "MMMM d, yyyy · hh:mm a", Locale.ENGLISH)) + " (PHT)";
        row = fact(facts, row, "Date and time", created);
        row = fact(facts, row, "Request",
                notification.getRequestId() == null ? "—"
                        : (notification.getRequestCode() == null ? "" : notification.getRequestCode() + "  ·  ")
                                + "ID " + notification.getRequestId());
        row = fact(facts, row, "Department",
                notification.getDepartment() == null ? "All / not department-specific"
                        : RequestStatus.departmentLabel(notification.getDepartment()));
        row = fact(facts, row, "Action", actionLabel(notification.getAction()));
        if (notification.getRequestStatus() != null) {
            row = fact(facts, row, "Current request stage",
                    UiSupport.displayStatus(notification.getRequestStatus()));
        }
        if (!notification.getRequestEquipment().isBlank()) {
            row = fact(facts, row, "Equipment", notification.getRequestEquipment());
        }
        if (notification.getEventDate() != null) {
            fact(facts, row, "Event date", notification.getEventDate().format(
                    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)));
        }

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(message, BorderLayout.NORTH);
        JPanel divider = new JPanel();
        divider.setOpaque(false);
        divider.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.border()));
        divider.setPreferredSize(new Dimension(1, 1));
        body.add(divider, BorderLayout.CENTER);
        body.add(facts, BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);
        content.add(card, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        if (delete != null) {
            PrimaryButton remove = new PrimaryButton("Delete", PrimaryButton.Style.DANGER);
            remove.addActionListener(event -> {
                if (!UiSupport.beginAction(remove)) {
                    return;
                }
                int answer = UiSupport.showConfirmDialog(this,
                        "Delete this notification? The related request is not affected.",
                        "Delete notification", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) {
                    dispose();
                    delete.run();
                } else {
                    UiSupport.endAction(remove);
                }
            });
            actions.add(remove);
        }
        if (viewRequest != null) {
            PrimaryButton view = new PrimaryButton("View request details");
            view.addActionListener(event -> {
                dispose();
                viewRequest.run();
            });
            actions.add(view);
        }
        PrimaryButton close = new PrimaryButton("Close", PrimaryButton.Style.SECONDARY);
        close.addActionListener(event -> dispose());
        actions.add(close);
        content.add(actions, BorderLayout.SOUTH);

        setContentPane(src.ui.components.Motion.popIn(content));
        setSize(new Dimension(560, 460));
        setMinimumSize(new Dimension(480, 380));
        setLocationRelativeTo(owner);
    }

    private static int fact(JPanel facts, int row, String label, String value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(3, 0, 3, 12);
        c.gridx = 0;
        JLabel key = new JLabel(label);
        key.setFont(AppTheme.semibold(10.5f));
        key.setForeground(AppTheme.muted());
        key.setPreferredSize(new Dimension(140, key.getPreferredSize().height));
        facts.add(key, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 0);
        String safe = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel detail = new JLabel("<html><div style='width:300px'>" + safe + "</div></html>");
        detail.setFont(AppTheme.regular(11.5f));
        detail.setForeground(AppTheme.foreground());
        facts.add(detail, c);
        return row + 1;
    }

    static String actionLabel(String action) {
        if (action == null || action.isBlank()) {
            return "Status update";
        }
        switch (action) {
            case "SUBMITTED": return "New request submitted";
            case "BORROWER_EDITED": return "Request updated by borrower";
            case "DEPARTMENT_APPROVED": return "Department approved its equipment";
            case "DEPARTMENT_DECLINED": return "Department declined its equipment";
            case "EQUIPMENT_RELEASED": return "Equipment released";
            case "BORROWER_RECEIVED": return "Borrower confirmed receipt";
            case "EQUIPMENT_RETURNED": return "Equipment returned";
            case "PENDING_ADMIN": return "Moved to administrative review";
            case "ADMIN_APPROVED": return "Approved by administrator";
            case "ADMIN_REJECTED": return "Declined by administrator";
            case "DIRECTOR_APPROVED": return "Final approval by campus director";
            case "DIRECTOR_REJECTED": return "Declined by campus director";
            case "DECLINED": return "Request declined";
            case "COMPLETED": return "Request completed";
            case "NEEDS_ACTION": return "Changes requested";
            case "OVERDUE": return "Not processed on time";
            case "REMINDER": return "Reminder";
            default: return UiSupport.displayStatus(action);
        }
    }
}
