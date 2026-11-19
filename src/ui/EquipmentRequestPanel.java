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

public class EquipmentRequestPanel extends EquipmentRequestPanelPart2 {

    public EquipmentRequestPanel(
            User user,
            BorrowRequestController requestController,
            EquipmentController equipmentController,
            BorrowRequest existingRequest,
            Runnable onSaved) {
        this(user, requestController, equipmentController, existingRequest, onSaved,
                List.of());
    }

    /** @param preselected items chosen in Available Equipment to start the request with */
    public EquipmentRequestPanel(
            User user,
            BorrowRequestController requestController,
            EquipmentController equipmentController,
            BorrowRequest existingRequest,
            Runnable onSaved,
            List<EquipmentRequestItem> preselected) {
        this.borrowerId = user.getUserId();
        this.facultyMonthly = "FACULTY".equalsIgnoreCase(user.getBorrowerType());
        this.actorRole = user == null || user.getRole() == null ? "" : user.getRole().trim();
        this.canManageRooms = "ICT_STAFF".equalsIgnoreCase(actorRole);
        this.requestController = requestController;
        this.equipmentController = equipmentController;
        this.existingRequest = existingRequest;
        this.onSaved = onSaved;
        this.saveButton = new PrimaryButton(
                existingRequest == null ? "Submit request" : "Save changes");

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        initializeValues(user);
        if (existingRequest == null && preselected != null) {
            selectedItems.addAll(preselected);
        }
        buildForm();
        loadEquipment();
    }

    void buildForm() {
        JPanel introduction = new JPanel(new BorderLayout(8, 3));
        introduction.setOpaque(false);
        JLabel heading = new JLabel(existingRequest == null
                ? "Tell us what you need" : "Update your request");
        heading.setFont(AppTheme.semibold(18));
        heading.setForeground(AppTheme.foreground());
        JLabel hint = new JLabel(facultyMonthly
                ? "Faculty monthly request: add one or more schedules, then add equipment."
                : "Complete the activity details, then add equipment from campus inventory.");
        hint.setFont(AppTheme.regular(11));
        hint.setForeground(AppTheme.muted());
        introduction.add(heading, BorderLayout.NORTH);
        introduction.add(hint, BorderLayout.CENTER);
        introduction.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(introduction);
        add(Box.createVerticalStrut(14));

        SurfacePanel eventCard = new SurfacePanel();
        eventCard.setLayout(new GridBagLayout());
        addSectionTitle(eventCard, "Activity details", 0);
        styleField(venue);
        styleField(eventDate);
        styleField(startTime);
        styleField(endTime);
        styleField(participants);
        styleField(personInCharge);
        styleField(position);
        styleArea(purpose);
        int row = 1;
        JComponent venueField = venue;
        if (canManageRooms) {
            JPanel venueRow = new JPanel(new BorderLayout(6, 0));
            venueRow.setOpaque(false);
            PrimaryButton addRoom = new PrimaryButton("Add room", PrimaryButton.Style.SECONDARY);
            addRoom.setToolTipText("Add a room to the Venue dropdown");
            addRoom.addActionListener(event -> addVenueRoom(addRoom));
            venueRow.add(venue, BorderLayout.CENTER);
            venueRow.add(addRoom, BorderLayout.EAST);
            venueField = venueRow;
        }
        addFormField(eventCard, "Venue", venueField, row, 0);
        addFormField(eventCard, "Event date (YYYY-MM-DD)", eventDate, row++, 1);
        addFormField(eventCard, "Start time (hh:mm + AM/PM)", startTime, row, 0);
        addFormField(eventCard, "End time (hh:mm + AM/PM)", endTime, row++, 1);
        addFormField(eventCard, "Participants", participants, row, 0);
        addFormField(eventCard, "Person in charge", personInCharge, row++, 1);
        addFormField(eventCard, "Position", position, row, 0);
        addFormField(eventCard, "Purpose", new JScrollPane(purpose), row++, 1);
        eventCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(eventCard);
        add(Box.createVerticalStrut(14));

        if (facultyMonthly) {
            SurfacePanel schedCard = new SurfacePanel();
            schedCard.setLayout(new BoxLayout(schedCard, BoxLayout.Y_AXIS));
            JLabel sh = new JLabel("Monthly schedules (each occurrence)");
            sh.setFont(AppTheme.semibold(15));
            sh.setForeground(AppTheme.foreground());
            schedCard.add(sh);
            schedCard.add(Box.createVerticalStrut(4));
            JLabel sn = new JLabel("Add all dates/times for this monthly request. Staff will assign equipment per schedule.");
            sn.setFont(AppTheme.regular(10));
            sn.setForeground(AppTheme.muted());
            schedCard.add(sn);
            schedCard.add(Box.createVerticalStrut(10));
            schedulesPanel.setOpaque(false);
            schedulesPanel.setLayout(new BoxLayout(schedulesPanel, BoxLayout.Y_AXIS));
            schedCard.add(schedulesPanel);
            PrimaryButton addSched = new PrimaryButton("Add schedule", PrimaryButton.Style.SECONDARY);
            addSched.addActionListener(e -> addScheduleFromFields());
            JPanel ap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            ap.setOpaque(false);
            ap.add(addSched);
            schedCard.add(Box.createVerticalStrut(8));
            schedCard.add(ap);
            schedCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(schedCard);
            add(Box.createVerticalStrut(14));
            renderSchedules();
        }

        SurfacePanel equipmentCard = new SurfacePanel();
        equipmentCard.setLayout(new BoxLayout(equipmentCard, BoxLayout.Y_AXIS));
        JLabel equipmentHeading = new JLabel("Equipment");
        equipmentHeading.setFont(AppTheme.semibold(15));
        equipmentHeading.setForeground(AppTheme.foreground());
        equipmentCard.add(equipmentHeading);
        equipmentCard.add(Box.createVerticalStrut(4));
        inventoryNote.setFont(AppTheme.regular(10));
        inventoryNote.setForeground(AppTheme.muted());
        equipmentCard.add(inventoryNote);
        equipmentCard.add(Box.createVerticalStrut(12));

        JPanel pickerRow = new JPanel(new BorderLayout(10, 0));
        pickerRow.setOpaque(false);
        equipmentPicker.setFont(AppTheme.regular(12));
        equipmentPicker.setRenderer(new EquipmentRenderer());
        equipmentPicker.setPreferredSize(new Dimension(300, 42));
        styleField(equipmentPicker);
        styleField(quantity);
        quantity.setPreferredSize(new Dimension(86, 42));
        JPanel pickerAndQuantity = new JPanel(new BorderLayout(8, 0));
        pickerAndQuantity.setOpaque(false);
        pickerAndQuantity.add(equipmentPicker, BorderLayout.CENTER);
        pickerAndQuantity.add(quantity, BorderLayout.EAST);
        PrimaryButton addItem = new PrimaryButton("Add item", PrimaryButton.Style.SECONDARY);
        addItem.addActionListener(event -> addSelectedItem());
        pickerRow.add(pickerAndQuantity, BorderLayout.CENTER);
        pickerRow.add(addItem, BorderLayout.EAST);
        equipmentCard.add(pickerRow);
        equipmentCard.add(Box.createVerticalStrut(14));

        selectedItemsPanel.setOpaque(false);
        selectedItemsPanel.setLayout(new BoxLayout(selectedItemsPanel, BoxLayout.Y_AXIS));
        equipmentCard.add(selectedItemsPanel);
        equipmentCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(equipmentCard);
        add(Box.createVerticalStrut(14));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        saveButton.addActionListener(event -> saveRequest());
        actions.add(saveButton);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(actions);
        renderSelectedItems();
        listenForScheduleChanges();
    }
}
