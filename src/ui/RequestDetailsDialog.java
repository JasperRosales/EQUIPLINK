package src.ui;

import src.model.BorrowRequest;
import src.model.DepartmentStatus;
import src.model.EquipmentRequestItem;
import src.model.RequestOccurrence;
import src.model.RequestStatus;
import src.ui.components.PrimaryButton;
import src.ui.components.StatusBadge;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only view of one request: activity details, schedules, purpose, each
 * department's decision and release record, and the equipment lines. Labels and
 * values sit in a two-column grid so long text wraps without breaking alignment.
 */
public class RequestDetailsDialog extends JDialog {

    private static final int LABEL_WIDTH = 138;
    private static final int VALUE_WIDTH = 440;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern(
            "MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern(
            "MMM d, yyyy · hh:mm a", Locale.ENGLISH);

    private final String scopeRole;
    private final src.model.User actor;
    private final src.controller.BorrowRequestController requestController;

    public RequestDetailsDialog(Window owner, BorrowRequest request) {
        this(owner, request, null, null, null);
    }

    public RequestDetailsDialog(Window owner, BorrowRequest request, String scopeRole) {
        this(owner, request, scopeRole, null, null);
    }

    /** @param scopeRole a custodian role to show only that department, or null for all. */
    public RequestDetailsDialog(
            Window owner,
            BorrowRequest request,
            String scopeRole,
            src.model.User actor,
            src.controller.BorrowRequestController requestController) {
        super(owner, "Request details · " + request.getRequestCode(),
                Dialog.ModalityType.APPLICATION_MODAL);
        this.scopeRole = scopeRole;
        this.actor = actor;
        this.requestController = requestController;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppTheme.background());
        content.setBorder(new EmptyBorder(20, 22, 16, 22));

        SurfacePanel summary = new SurfacePanel();
        summary.setLayout(new BorderLayout(8, 6));
        JLabel code = new JLabel(request.getRequestCode());
        code.setFont(AppTheme.semibold(18));
        code.setForeground(AppTheme.foreground());
        JLabel borrower = new JLabel(request.getBorrowerName() == null ? ""
                : "Requested by " + request.getBorrowerName());
        borrower.setFont(AppTheme.regular(11));
        borrower.setForeground(AppTheme.muted());
        JPanel heading = new JPanel(new BorderLayout(0, 3));
        heading.setOpaque(false);
        heading.add(code, BorderLayout.NORTH);
        heading.add(borrower, BorderLayout.CENTER);
        summary.add(heading, BorderLayout.WEST);
        summary.add(UiSupport.statusBadges(request, scopeRole, false), BorderLayout.EAST);
        addSection(content, summary);

        Section activity = new Section("Activity details");
        activity.row("Venue", request.getVenue());
        activity.row("Event date", request.getEventDate().format(DATE_LONG));
        activity.row("Time", UiSupport.timeRange(request.getStartTime(), request.getEndTime()));
        activity.row("Participants", request.getParticipants());
        activity.row("Person in charge", request.getPersonInCharge());
        activity.row("Position", request.getPosition());
        activity.row("Submitted", request.getSubmittedAt().format(STAMP));
        activity.row("Review stage", UiSupport.displayStatus(request.getStatus()));
        activity.row("Approval progress", RequestStatus.approvalProgress(request, scopeRole));
        activity.row("Remarks", blankAsDash(request.getRemarks()));
        addSection(content, activity);

        if (request.getOccurrences().size() > 1) {
            Section schedules = new Section("Schedules (" + request.getOccurrences().size() + ")");
            int index = 1;
            for (RequestOccurrence occurrence : request.getOccurrences()) {
                schedules.row("Schedule " + index++,
                        occurrence.getEventDate().format(DATE) + "  ·  "
                                + UiSupport.timeRange(occurrence.getStartTime(),
                                        occurrence.getEndTime())
                                + "  ·  " + blankAsDash(occurrence.getVenue()));
            }
            addSection(content, schedules);
        }

        SurfacePanel purpose = new SurfacePanel();
        purpose.setLayout(new BorderLayout(0, 8));
        purpose.add(title("Purpose"), BorderLayout.NORTH);
        JTextArea purposeText = new JTextArea(request.getPurpose());
        purposeText.setLineWrap(true);
        purposeText.setWrapStyleWord(true);
        purposeText.setEditable(false);
        purposeText.setOpaque(false);
        purposeText.setBorder(null);
        purposeText.setFont(AppTheme.regular(12));
        purposeText.setForeground(AppTheme.foreground());
        purpose.add(purposeText, BorderLayout.CENTER);
        addSection(content, purpose);

        for (String role : new String[] { RequestStatus.ICT, RequestStatus.FACILITY }) {
            if (scopeRole != null && !scopeRole.equalsIgnoreCase(role)) {
                continue;
            }
            Section department = departmentSection(request, role);
            if (department != null) {
                addSection(content, department);
            }
        }

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(4, 0, 0, 0));
        actions.setAlignmentX(LEFT_ALIGNMENT);
        if (requestController != null
                && requestController.canExportRequestForm(actor, request)) {
            PrimaryButton download = new PrimaryButton("Download request form");
            download.setToolTipText("Download an official copy for physical documentation");
            download.addActionListener(event -> exportRequestForm(request));
            actions.add(download);
        }
        PrimaryButton close = new PrimaryButton("Close", PrimaryButton.Style.SECONDARY);
        close.addActionListener(event -> dispose());
        actions.add(close);
        content.add(actions);

        JScrollPane scroll = UiSupport.scroll(content);
        scroll.setPreferredSize(new Dimension(720, 640));
        setContentPane(scroll);
        pack();
        setMinimumSize(new Dimension(640, 520));
        setLocationRelativeTo(owner);
    }

    private void exportRequestForm(BorrowRequest request) {
        try {
            requestController.verifyRequestFormExport(actor, request);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save request form");
            chooser.setSelectedFile(new File(safeFileName(request.getRequestCode()) + ".html"));
            chooser.setFileFilter(new FileNameExtensionFilter("HTML document", "html"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File target = chooser.getSelectedFile();
            if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".html")) {
                target = new File(target.getParentFile(), target.getName() + ".html");
            }
            Files.writeString(target.toPath(), requestFormHtml(request), StandardCharsets.UTF_8);
            UiSupport.showMessageDialog(this,
                    "Request form saved to:\n" + target.getAbsolutePath(),
                    "Request form downloaded", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            UiSupport.showMessageDialog(this,
                    "Unable to download request form: " + exception.getMessage(),
                    "Download failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String requestFormHtml(BorrowRequest request) {
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="UTF-8">
                <title>EquipLink Request Form</title>
                <style>
                body { font-family: Arial, sans-serif; margin: 32px; color: #1b2b32; }
                h1 { color: #0d6b7a; margin-bottom: 2px; }
                h2 { margin-top: 26px; font-size: 15px; color: #0d6b7a; }
                table { border-collapse: collapse; width: 100%; margin-top: 8px; }
                th, td { border: 1px solid #cbd8dd; padding: 7px 9px; text-align: left;
                         font-size: 12px; vertical-align: top; }
                th { background: #eef5f6; width: 180px; }
                .note { color: #62757c; font-size: 11px; }
                </style></head><body>
                """);
        html.append("<h1>EquipLink Request Form</h1>");
        html.append("<p class=\"note\">Approved/completed request · Generated ")
                .append(LocalDateTime.now().format(STAMP)).append("</p>");
        html.append("<table>");
        appendRow(html, "Request code", request.getRequestCode());
        appendRow(html, "Requested by", request.getBorrowerName());
        appendRow(html, "Venue", request.getVenue());
        appendRow(html, "Event date", request.getEventDate().format(DATE_LONG));
        appendRow(html, "Time", UiSupport.timeRange(request.getStartTime(), request.getEndTime()));
        appendRow(html, "Participants", request.getParticipants());
        appendRow(html, "Person in charge", request.getPersonInCharge());
        appendRow(html, "Position", request.getPosition());
        appendRow(html, "Submitted", request.getSubmittedAt().format(STAMP));
        appendRow(html, "Review stage", UiSupport.displayStatus(request.getStatus()));
        appendRow(html, "Remarks", request.getRemarks());
        html.append("</table>");

        html.append("<h2>Purpose</h2>");
        html.append("<p>").append(escape(request.getPurpose())).append("</p>");

        html.append("<h2>Schedules</h2><table><tr><th>Schedule</th><th>Date</th><th>Time</th><th>Venue</th></tr>");
        int index = 1;
        List<RequestOccurrence> occurrences = request.getOccurrences() == null
                ? List.of() : request.getOccurrences();
        if (occurrences.isEmpty()) {
            html.append("<tr><td>").append(index++).append("</td><td>")
                    .append(escape(request.getEventDate() == null ? "" : request.getEventDate().format(DATE)))
                    .append("</td><td>")
                    .append(escape(UiSupport.timeRange(request.getStartTime(), request.getEndTime())))
                    .append("</td><td>").append(escape(request.getVenue())).append("</td></tr>");
        }
        for (RequestOccurrence occurrence : occurrences) {
            html.append("<tr><td>").append(index++).append("</td><td>")
                    .append(escape(occurrence.getEventDate().format(DATE)))
                    .append("</td><td>")
                    .append(escape(UiSupport.timeRange(occurrence.getStartTime(), occurrence.getEndTime())))
                    .append("</td><td>").append(escape(occurrence.getVenue())).append("</td></tr>");
        }
        html.append("</table>");

        html.append("<h2>Equipment</h2><table><tr><th>Department</th><th>Equipment</th>"
                + "<th>Code</th><th>Requested</th><th>Assigned</th><th>Released</th><th>Returned</th></tr>");
        for (EquipmentRequestItem item : request.getEquipmentItems()) {
            html.append("<tr><td>")
                    .append(escape(RequestStatus.departmentLabel(item.getCustodianRole())))
                    .append("</td><td>").append(escape(item.getEquipmentName()))
                    .append("</td><td>").append(escape(item.getEquipmentCode()))
                    .append("</td><td>").append(item.getRequestedQuantity())
                    .append("</td><td>").append(item.getAssignedQuantity())
                    .append("</td><td>").append(item.getReleasedQuantity())
                    .append("</td><td>").append(item.getReturnedQuantity())
                    .append("</td></tr>");
        }
        html.append("</table></body></html>");
        return html.toString();
    }

    private static void appendRow(StringBuilder html, String label, String value) {
        html.append("<tr><th>").append(escape(label)).append("</th><td>")
                .append(escape(value == null || value.isBlank() ? "—" : value))
                .append("</td></tr>");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String safeFileName(String value) {
        String safe = value == null ? "request-form" : value.replaceAll("[^A-Za-z0-9_-]", "-");
        return safe.isBlank() ? "request-form" : safe;
    }

    /** Decision, remarks, release record, and equipment lines for one department. */
    private Section departmentSection(BorrowRequest request, String role) {
        String status = RequestStatus.department(request, role);
        List<EquipmentRequestItem> items = new ArrayList<>();
        for (EquipmentRequestItem item : request.getEquipmentItems()) {
            if (role.equalsIgnoreCase(item.getCustodianRole())) {
                items.add(item);
            }
        }
        if (status == null && items.isEmpty()) {
            return null;
        }
        String label = RequestStatus.departmentLabel(role);
        Section section = new Section(label + " equipment");
        section.badge(new StatusBadge(UiSupport.departmentStatus(status),
                UiSupport.departmentStatusColor(status)));

        List<DepartmentStatus> groups = new ArrayList<>();
        for (DepartmentStatus group : request.getDepartments()) {
            if (role.equalsIgnoreCase(group.getCustodianRole())) {
                groups.add(group);
            }
        }
        boolean multiple = groups.size() > 1;
        for (DepartmentStatus group : groups) {
            String prefix = multiple && group.getScheduleStart() != null
                    ? group.getScheduleStart().format(DATE) + " · " : "";
            String decision = UiSupport.departmentStatus(group.getStatus());
            if (group.isNotAssignedOnTime(LocalDateTime.now())
                    && "PENDING_STAFF".equals(request.getStatus())) {
                decision = "Not Assigned (not processed on time)";
            }
            String by = group.getDecidedByName() == null ? ""
                    : " by " + group.getDecidedByName()
                            + (group.getDecidedAt() == null ? ""
                                    : " on " + group.getDecidedAt().format(STAMP));
            section.row(multiple ? prefix.isEmpty() ? "Decision" : prefix + "decision" : "Decision",
                    decision + by);
            if (group.getRemarks() != null && !group.getRemarks().isBlank()) {
                section.row(multiple ? prefix + "remarks" : "Remarks", group.getRemarks());
            }
            if (group.getReleasedAt() != null) {
                section.row(multiple ? prefix + "released" : "Released",
                        group.getReleasedAt().format(STAMP)
                                + (group.getReleasedByName() == null ? ""
                                        : " by " + group.getReleasedByName()));
            }
            if (group.getReceivedAt() != null) {
                section.row(multiple ? prefix + "received" : "Received",
                        group.getReceivedAt().format(STAMP)
                                + (group.getReceivedByName() == null ? ""
                                        : " by " + group.getReceivedByName()));
            }
            if (group.getInspectedAt() != null) {
                section.row(multiple ? prefix + "returned" : "Returned",
                        group.getInspectedAt().format(STAMP)
                                + (group.getInspectedByName() == null ? ""
                                        : " by " + group.getInspectedByName()));
            }
        }

        if (items.isEmpty()) {
            section.row("Equipment", "No items recorded");
        } else {
            section.itemHeader();
            for (EquipmentRequestItem item : items) {
                section.item(item);
            }
        }
        return section;
    }

    private void addSection(JPanel content, JComponent section) {
        section.setAlignmentX(LEFT_ALIGNMENT);
        content.add(section);
        content.add(Box.createVerticalStrut(12));
    }

    private static JLabel title(String text) {
        JLabel title = new JLabel(text);
        title.setFont(AppTheme.semibold(13));
        title.setForeground(AppTheme.foreground());
        return title;
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String html(String text, int width) {
        String safe = (text == null ? "—" : text)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html><div style='width:" + width + "px'>" + safe + "</div></html>";
    }

    /** A titled card with a two-column, top-aligned label/value grid. */
    private static final class Section extends SurfacePanel {

        private final JPanel header = new JPanel(new BorderLayout(8, 0));
        private final JPanel grid = new JPanel(new GridBagLayout());
        private int row;

        Section(String text) {
            setLayout(new BorderLayout(0, 10));
            header.setOpaque(false);
            header.add(title(text), BorderLayout.WEST);
            grid.setOpaque(false);
            add(header, BorderLayout.NORTH);
            add(grid, BorderLayout.CENTER);
        }

        void badge(JComponent badge) {
            header.add(badge, BorderLayout.EAST);
        }

        void row(String label, String value) {
            JLabel key = new JLabel(html(label, LABEL_WIDTH - 12));
            key.setFont(AppTheme.semibold(10.5f));
            key.setForeground(AppTheme.muted());
            JLabel detail = new JLabel(html(value, VALUE_WIDTH));
            detail.setFont(AppTheme.regular(11.5f));
            detail.setForeground(AppTheme.foreground());
            add(key, 0, 1, LABEL_WIDTH);
            add(detail, 1, 5, 0);
            row++;
        }

        /** Equipment table: one shared grid so every column lines up across rows. */
        private JPanel items;
        private int itemRow;

        void itemHeader() {
            items = new JPanel(new GridBagLayout());
            items.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 2;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(10, 0, 0, 0);
            grid.add(items, c);
            String[] headers = { "Equipment", "Requested", "Assigned", "Released", "Returned" };
            for (int i = 0; i < headers.length; i++) {
                JLabel cell = new JLabel(headers[i]);
                cell.setFont(AppTheme.semibold(10.5f));
                cell.setForeground(AppTheme.muted());
                if (i > 0) {
                    cell.setHorizontalAlignment(JLabel.RIGHT);
                }
                addCell(cell, i, false);
            }
            itemRow++;
        }

        void item(EquipmentRequestItem item) {
            StringBuilder name = new StringBuilder("<b>")
                    .append(escape(item.getEquipmentName())).append("</b><br>")
                    .append(escape(item.getEquipmentCode())).append(" · ")
                    .append(escape(item.getCategory()));
            if (item.getConditionBefore() != null) {
                name.append("<br>Before: ").append(escape(item.getConditionBefore()));
            }
            if (item.getConditionAfter() != null) {
                name.append("<br>After: ").append(escape(item.getConditionAfter()));
            }
            if (item.getDamagedQuantity() > 0 || item.getMissingQuantity() > 0) {
                name.append("<br>Damaged ").append(item.getDamagedQuantity())
                        .append(" · Missing ").append(item.getMissingQuantity());
            }
            JLabel label = new JLabel("<html><div style='width:240px'>" + name + "</div></html>");
            label.setFont(AppTheme.regular(11));
            label.setForeground(AppTheme.foreground());
            addCell(label, 0, true);
            int[] values = { item.getRequestedQuantity(), item.getAssignedQuantity(),
                    item.getReleasedQuantity(), item.getReturnedQuantity() };
            for (int i = 0; i < values.length; i++) {
                JLabel value = new JLabel(Integer.toString(values[i]), JLabel.RIGHT);
                value.setFont(AppTheme.regular(11.5f));
                value.setForeground(AppTheme.foreground());
                addCell(value, i + 1, true);
            }
            itemRow++;
        }

        private void addCell(JComponent cell, int column, boolean divider) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = column;
            c.gridy = itemRow;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = column == 0 ? 1 : 0;
            c.ipadx = column == 0 ? 0 : 18;
            if (divider) {
                cell.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                        javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.border()),
                        new EmptyBorder(7, column == 0 ? 0 : 10, 7, 0)));
            } else {
                cell.setBorder(new EmptyBorder(0, column == 0 ? 0 : 10, 5, 0));
            }
            items.add(cell, c);
        }

        private static String escape(String text) {
            return text == null ? "" : text.replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;");
        }

        private void add(JComponent component, int column, int weight, int width) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = column;
            c.gridy = row;
            c.weightx = weight;
            c.anchor = GridBagConstraints.NORTHWEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(4, 0, 4, column == 0 ? 12 : 0);
            if (width > 0) {
                component.setPreferredSize(new Dimension(width,
                        component.getPreferredSize().height));
                component.setMinimumSize(component.getPreferredSize());
            }
            grid.add(component, c);
        }
    }
}
