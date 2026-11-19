package src.ui;

import src.model.BorrowRequest;
import src.model.BorrowRequestForm;
import src.model.Equipment;
import src.model.EquipmentRequestItem;
import src.model.RequestOccurrence;
import src.model.User;
import src.controller.BorrowRequestController;
import src.controller.EquipmentController;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;
import src.ui.components.VenueOptions;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Part 1 of EquipmentRequestPanel, split out to keep files short. */
public abstract class EquipmentRequestPanelPart1 extends JPanel {

    long borrowerId;
    boolean facultyMonthly;
    String actorRole;
    boolean canManageRooms;
    BorrowRequestController requestController;
    EquipmentController equipmentController;
    BorrowRequest existingRequest;
    Runnable onSaved;
    final JComboBox<Equipment> equipmentPicker = new JComboBox<>();
    final JSpinner quantity = new JSpinner(
            new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    final JComboBox<String> venue = new JComboBox<>();
    final JTextField eventDate = new JTextField();
    final src.ui.components.TimeInput startTime = new src.ui.components.TimeInput();
    final src.ui.components.TimeInput endTime = new src.ui.components.TimeInput();
    final JTextField participants = new JTextField();
    final JTextField personInCharge = new JTextField();
    final JTextField position = new JTextField();
    final JTextArea purpose = new JTextArea(4, 25);
    final JPanel selectedItemsPanel = new JPanel();
    final JPanel schedulesPanel = new JPanel();
    final JLabel inventoryNote = new JLabel("Loading campus inventory…");
    PrimaryButton saveButton;
    final List<Equipment> availableEquipment = new ArrayList<>();
    final List<EquipmentRequestItem> selectedItems = new ArrayList<>();
    final List<ScheduleRow> scheduleRows = new ArrayList<>();
    Timer availabilityTimer;

    void initializeValues(User user) {
        LocalDate eventDay = LocalDate.now().plusDays(1);
        eventDate.setText(eventDay.format(DateTimeFormatter.ISO_LOCAL_DATE));
        startTime.setText("09:00 AM");
        endTime.setText("10:00 AM");
        participants.setText("1");
        personInCharge.setText(user.getFullName());
        position.setText("Borrower");
        purpose.setLineWrap(true);
        purpose.setWrapStyleWord(true);
        for (String room : VenueOptions.load()) {
            venue.addItem(room);
        }
        venue.setEditable(true);
        venue.setToolTipText("Choose a room, or type a different venue name");
        venue.setSelectedIndex(-1);
        venue.getEditor().setItem("");
        if (venue.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.putClientProperty("JTextField.placeholderText",
                    "Choose a room or type a venue");
        }

        if (existingRequest != null) {
            venue.setSelectedItem(existingRequest.getVenue() == null ? ""
                    : existingRequest.getVenue().toUpperCase());
            eventDate.setText(existingRequest.getEventDate().toString());
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
            startTime.setText(existingRequest.getStartTime().format(timeFmt));
            endTime.setText(existingRequest.getEndTime().format(timeFmt));
            participants.setText(existingRequest.getParticipants());
            personInCharge.setText(existingRequest.getPersonInCharge());
            position.setText(existingRequest.getPosition());
            purpose.setText(existingRequest.getPurpose());
            selectedItems.addAll(existingRequest.getEquipmentItems());
            if (existingRequest.getOccurrences() != null && !existingRequest.getOccurrences().isEmpty()) {
                for (RequestOccurrence o : existingRequest.getOccurrences()) {
                    scheduleRows.add(new ScheduleRow(o.getEventDate(), o.getStartTime(), o.getEndTime(), o.getVenue()));
                }
            } else if (facultyMonthly) {
                scheduleRows.add(new ScheduleRow(existingRequest.getEventDate(), existingRequest.getStartTime(),
                        existingRequest.getEndTime(), existingRequest.getVenue()));
            }
        } else if (facultyMonthly) {
            // start with one empty schedule row prefilled from fields after init
            scheduleRows.add(new ScheduleRow(LocalDate.now().plusDays(1), LocalTime.of(9,0), LocalTime.of(10,0),
                    venueText()));
        }
    }

    /** The editable venue picker's current text, whether picked or typed manually. */
    String venueText() {
        Object value = venue.isEditable()
                ? venue.getEditor().getItem() : venue.getSelectedItem();
        return value == null ? "" : value.toString().trim();
    }

    void addVenueRoom(javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        if (!canManageRooms) {
            UiSupport.endAction(source);
            UiSupport.showMessageDialog(this,
                    "Only the ICT System Admin can add rooms.", "Not authorized",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        String room = UiSupport.showInputDialog(
                this, "Room or venue name:", "Add room",
                javax.swing.JOptionPane.QUESTION_MESSAGE);
        if (room == null || room.isBlank()) {
            UiSupport.endAction(source);
            return;
        }
        try {
            String current = venueText();
            boolean added = VenueOptions.addRoom(room, actorRole);
            venue.removeAllItems();
            for (String option : VenueOptions.load()) {
                venue.addItem(option);
            }
            venue.setSelectedIndex(-1);
            venue.getEditor().setItem(current);
            UiSupport.showMessageDialog(this,
                    added ? "Room added to the Venue dropdown."
                            : "That room is already in the Venue dropdown.",
                    added ? "Room added" : "Room already exists",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            UiSupport.showMessageDialog(this,
                    "Unable to add room: " + exception.getMessage(), "Room not added",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        } finally {
            UiSupport.endAction(source);
        }
    }

    void addSectionTitle(JPanel panel, String text, int row) {
        JLabel title = new JLabel(text);
        title.setFont(AppTheme.semibold(15));
        title.setForeground(AppTheme.foreground());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 10, 0);
        panel.add(title, c);
    }

    void addFormField(
            JPanel panel,
            String label,
            JComponent field,
            int row,
            int column) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(AppTheme.semibold(10));
        fieldLabel.setForeground(AppTheme.muted());
        JPanel fieldGroup = new JPanel();
        fieldGroup.setOpaque(false);
        fieldGroup.setLayout(new BoxLayout(fieldGroup, BoxLayout.Y_AXIS));
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldGroup.add(fieldLabel);
        fieldGroup.add(Box.createVerticalStrut(6));
        fieldGroup.add(field);
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = column;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
        fieldConstraints.insets = new Insets(6, 5, 6, 12);
        panel.add(fieldGroup, fieldConstraints);
    }

    void styleField(JComponent field) {
        field.setFont(AppTheme.regular(12));
        field.setForeground(AppTheme.foreground());
        field.setBackground(AppTheme.surface());
    }

    void styleArea(JTextArea area) {
        area.setFont(AppTheme.regular(12));
        area.setForeground(AppTheme.foreground());
        area.setBackground(AppTheme.surface());
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
    }

    LocalTime parseTime(String value) {
        String text = value == null ? "" : value.trim().toUpperCase(Locale.ENGLISH).replace("  ", " ");
        DateTimeParseException last = null;
        for (DateTimeFormatter fmt : new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("HH:mm") }) {
            try {
                return LocalTime.parse(text, fmt);
            } catch (DateTimeParseException e) {
                last = e;
            }
        }
        throw last;
    }

    void scheduleAvailabilityRefresh() {
        if (availabilityTimer == null) {
            return;
        }
        equipmentPicker.setEnabled(false);
        inventoryNote.setForeground(AppTheme.muted());
        inventoryNote.setText("Updating availability for the selected schedule…");
        availabilityTimer.restart();
    }

    EquipmentRequestItem createItem(Equipment equipment, int requested) {
        return new EquipmentRequestItem(
                equipment.getEquipmentId(),
                equipment.getEquipmentCode(),
                equipment.getEquipmentName(),
                equipment.getCategory(),
                requested,
                0, 0, 0);
    }

    void renderSchedules() {
        schedulesPanel.removeAll();
        if (scheduleRows.isEmpty()) {
            JLabel empty = new JLabel("No schedules added yet. Fill the fields above and click \"Add schedule\".");
            empty.setFont(AppTheme.regular(11));
            empty.setForeground(AppTheme.muted());
            schedulesPanel.add(empty);
        } else {
            DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
            for (int i = 0; i < scheduleRows.size(); i++) {
                ScheduleRow r = scheduleRows.get(i);
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(true);
                row.setBackground(AppTheme.elevatedSurface());
                row.setBorder(new EmptyBorder(8, 10, 8, 8));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
                JLabel lbl = new JLabel((i + 1) + ". " + r.date + "  " + r.start.format(tf) + "–" + r.end.format(tf) + "  ·  " + r.venue);
                lbl.setFont(AppTheme.regular(11));
                lbl.setForeground(AppTheme.foreground());
                PrimaryButton rm = new PrimaryButton("Remove", PrimaryButton.Style.DANGER);
                int idx = i;
                rm.addActionListener(e -> { scheduleRows.remove(idx); renderSchedules(); });
                row.add(lbl, BorderLayout.CENTER);
                row.add(rm, BorderLayout.EAST);
                schedulesPanel.add(row);
                schedulesPanel.add(Box.createVerticalStrut(6));
            }
        }
        schedulesPanel.revalidate();
        schedulesPanel.repaint();
    }

    void renderSelectedItems() {
        selectedItemsPanel.removeAll();
        if (selectedItems.isEmpty()) {
            JLabel empty = new JLabel("No equipment added yet.");
            empty.setFont(AppTheme.regular(11));
            empty.setForeground(AppTheme.muted());
            selectedItemsPanel.add(empty);
        } else {
            for (int i = 0; i < selectedItems.size(); i++) {
                EquipmentRequestItem item = selectedItems.get(i);
                JPanel row = new JPanel(new BorderLayout(10, 2));
                row.setOpaque(true);
                row.setBackground(AppTheme.elevatedSurface());
                row.setBorder(new EmptyBorder(9, 12, 9, 9));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
                JPanel info = new JPanel(new BorderLayout(0, 3));
                info.setOpaque(false);
                JLabel name = new JLabel(item.getEquipmentName());
                name.setFont(AppTheme.semibold(12));
                name.setForeground(AppTheme.foreground());
                JLabel detail = new JLabel(item.getEquipmentCode() + "  ·  "
                        + item.getCategory() + "  ·  Qty " + item.getRequestedQuantity());
                detail.setFont(AppTheme.regular(10));
                detail.setForeground(AppTheme.muted());
                info.add(name, BorderLayout.NORTH);
                info.add(detail, BorderLayout.CENTER);
                PrimaryButton remove = new PrimaryButton("Remove", PrimaryButton.Style.DANGER);
                int index = i;
                remove.addActionListener(event -> {
                    selectedItems.remove(index);
                    renderSelectedItems();
                });
                row.add(info, BorderLayout.CENTER);
                row.add(remove, BorderLayout.EAST);
                selectedItemsPanel.add(row);
                selectedItemsPanel.add(Box.createVerticalStrut(6));
            }
        }
        selectedItemsPanel.revalidate();
        selectedItemsPanel.repaint();
    }

    static class ScheduleRow {
        final LocalDate date; final LocalTime start; final LocalTime end; final String venue;
        ScheduleRow(LocalDate d, LocalTime s, LocalTime e, String v){date=d;start=s;end=e;venue=v==null?"":v.toUpperCase(Locale.ROOT);}
        RequestOccurrence toOccurrence(){ return new RequestOccurrence(date,start,end,venue); }
    }
}
