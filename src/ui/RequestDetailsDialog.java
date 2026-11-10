package src.ui;

import src.model.BorrowRequest;
import src.model.EquipmentRequestItem;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.format.DateTimeFormatter;

public class RequestDetailsDialog extends JDialog {

    public RequestDetailsDialog(Window owner, BorrowRequest request) {
        super(owner, "Request details · " + request.getRequestCode(),
                Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppTheme.background());
        content.setBorder(new EmptyBorder(20, 22, 16, 22));

        SurfacePanel summary = new SurfacePanel();
        summary.setLayout(new BorderLayout(8, 8));
        JLabel code = new JLabel(request.getRequestCode());
        code.setFont(AppTheme.semibold(18));
        code.setForeground(AppTheme.foreground());
        JLabel status = new JLabel(UiSupport.displayStatus(request.getStatus()));
        status.setFont(AppTheme.semibold(11));
        status.setForeground(UiSupport.statusColor(request.getStatus()));
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        summary.add(code, BorderLayout.WEST);
        summary.add(status, BorderLayout.EAST);
        content.add(summary);
        content.add(Box.createVerticalStrut(12));

        SurfacePanel activity = new SurfacePanel();
        activity.setLayout(new BoxLayout(activity, BoxLayout.Y_AXIS));
        addTitle(activity, "Activity details");
        addInfo(activity, "Venue", request.getVenue());
        addInfo(activity, "Event date",
                request.getEventDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        addInfo(activity, "Time", request.getStartTime() + " – " + request.getEndTime());
        addInfo(activity, "Participants", request.getParticipants());
        addInfo(activity, "Person in charge", request.getPersonInCharge());
        addInfo(activity, "Position", request.getPosition());
        addInfo(activity, "Submitted",
                request.getSubmittedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")));
        addInfo(activity, "Remarks", blankAsDash(request.getRemarks()));
        content.add(activity);
        content.add(Box.createVerticalStrut(12));

        SurfacePanel purpose = new SurfacePanel();
        purpose.setLayout(new BoxLayout(purpose, BoxLayout.Y_AXIS));
        addTitle(purpose, "Purpose");
        JTextArea purposeText = new JTextArea(request.getPurpose());
        purposeText.setLineWrap(true);
        purposeText.setWrapStyleWord(true);
        purposeText.setEditable(false);
        purposeText.setOpaque(false);
        purposeText.setFont(AppTheme.regular(12));
        purposeText.setForeground(AppTheme.foreground());
        purpose.add(purposeText);
        content.add(purpose);
        content.add(Box.createVerticalStrut(12));

        SurfacePanel equipment = new SurfacePanel();
        equipment.setLayout(new BoxLayout(equipment, BoxLayout.Y_AXIS));
        addTitle(equipment, "Requested equipment");
        if (request.getEquipmentItems().isEmpty()) {
            addInfo(equipment, "Equipment", "No items recorded");
        } else {
            for (EquipmentRequestItem item : request.getEquipmentItems()) {
                addInfo(equipment, item.getEquipmentName(),
                        item.getEquipmentCode() + " · " + item.getCategory()
                                + " · Requested " + item.getRequestedQuantity()
                        + " · Assigned " + item.getAssignedQuantity()
                        + " · Released " + item.getReleasedQuantity()
                                + " · Returned " + item.getReturnedQuantity());
            }
        }
        content.add(equipment);

        PrimaryButton close = new PrimaryButton("Close", PrimaryButton.Style.SECONDARY);
        close.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(12, 0, 0, 0));
        actions.add(close);
        content.add(actions);

        JScrollPane scroll = UiSupport.scroll(content);
        scroll.setPreferredSize(new Dimension(690, 620));
        setContentPane(scroll);
        pack();
        setMinimumSize(new Dimension(620, 560));
        setLocationRelativeTo(owner);
    }

    private void addTitle(JPanel panel, String text) {
        JLabel title = new JLabel(text);
        title.setFont(AppTheme.semibold(13));
        title.setForeground(AppTheme.foreground());
        title.setBorder(new EmptyBorder(0, 0, 9, 0));
        panel.add(title);
    }

    private void addInfo(JPanel panel, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(14, 4));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(3, 0, 3, 0));
        JLabel key = new JLabel(label);
        key.setFont(AppTheme.regular(10));
        key.setForeground(AppTheme.muted());
        key.setPreferredSize(new Dimension(132, 22));
        JLabel detail = new JLabel(value == null ? "—" : value);
        detail.setFont(AppTheme.regular(11));
        detail.setForeground(AppTheme.foreground());
        row.add(key, BorderLayout.WEST);
        row.add(detail, BorderLayout.CENTER);
        panel.add(row);
    }

    private String blankAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
