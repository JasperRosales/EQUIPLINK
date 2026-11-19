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
public class RequestDetailsDialog extends RequestDetailsDialogPart1 {

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
        setContentPane(src.ui.components.Motion.popIn(scroll));
        pack();
        setMinimumSize(new Dimension(640, 520));
        setLocationRelativeTo(owner);
    }

    /** Decision, remarks, release record, and equipment lines for one department. */
    Section departmentSection(BorrowRequest request, String role) {
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
}
