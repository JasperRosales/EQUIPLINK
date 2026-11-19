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

/** Part 1 of RequestDetailsDialog, split out to keep files short. */
public abstract class RequestDetailsDialogPart1 extends JDialog {

    protected RequestDetailsDialogPart1(java.awt.Window owner, String title, java.awt.Dialog.ModalityType modality) {
        super(owner, title, modality);
    }


    static final int LABEL_WIDTH = 138;
    static final int VALUE_WIDTH = 440;
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "MMM d, yyyy", Locale.ENGLISH);
    static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern(
            "MMMM d, yyyy", Locale.ENGLISH);
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern(
            "MMM d, yyyy · hh:mm a", Locale.ENGLISH);

    String scopeRole;
    src.model.User actor;
    src.controller.BorrowRequestController requestController;

    void exportRequestForm(BorrowRequest request) {
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

    String requestFormHtml(BorrowRequest request) {
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

    static void appendRow(StringBuilder html, String label, String value) {
        html.append("<tr><th>").append(escape(label)).append("</th><td>")
                .append(escape(value == null || value.isBlank() ? "—" : value))
                .append("</td></tr>");
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String safeFileName(String value) {
        String safe = value == null ? "request-form" : value.replaceAll("[^A-Za-z0-9_-]", "-");
        return safe.isBlank() ? "request-form" : safe;
    }

    void addSection(JPanel content, JComponent section) {
        section.setAlignmentX(LEFT_ALIGNMENT);
        content.add(section);
        content.add(Box.createVerticalStrut(12));
    }

    static JLabel title(String text) {
        JLabel title = new JLabel(text);
        title.setFont(AppTheme.semibold(13));
        title.setForeground(AppTheme.foreground());
        return title;
    }

    static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    static String html(String text, int width) {
        String safe = (text == null ? "—" : text)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html><div style='width:" + width + "px'>" + safe + "</div></html>";
    }

    /** A titled card with a two-column, top-aligned label/value grid. */
    static final class Section extends SurfacePanel {

        final JPanel header = new JPanel(new BorderLayout(8, 0));
        final JPanel grid = new JPanel(new GridBagLayout());
        int row;

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
        JPanel items;
        int itemRow;

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

        void addCell(JComponent cell, int column, boolean divider) {
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

        static String escape(String text) {
            return text == null ? "" : text.replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;");
        }

        void add(JComponent component, int column, int weight, int width) {
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
