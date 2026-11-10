package src.ui;

import src.model.BorrowRequest;
import src.model.BorrowRequestForm;
import src.model.Equipment;
import src.model.EquipmentRequestItem;
import src.model.User;
import src.controller.BorrowRequestController;
import src.controller.EquipmentController;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

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

public class EquipmentRequestPanel extends JPanel {

    private final long borrowerId;
    private final BorrowRequestController requestController;
    private final EquipmentController equipmentController;
    private final BorrowRequest existingRequest;
    private final Runnable onSaved;
    private final JComboBox<Equipment> equipmentPicker = new JComboBox<>();
    private final JSpinner quantity = new JSpinner(
            new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JTextField venue = new JTextField();
    private final JTextField eventDate = new JTextField();
    private final JTextField startTime = new JTextField();
    private final JTextField endTime = new JTextField();
    private final JTextField participants = new JTextField();
    private final JTextField personInCharge = new JTextField();
    private final JTextField position = new JTextField();
    private final JTextArea purpose = new JTextArea(4, 25);
    private final JPanel selectedItemsPanel = new JPanel();
    private final JLabel inventoryNote = new JLabel("Loading campus inventory…");
    private final PrimaryButton saveButton;
    private final List<Equipment> availableEquipment = new ArrayList<>();
    private final List<EquipmentRequestItem> selectedItems = new ArrayList<>();
    private Timer availabilityTimer;

    public EquipmentRequestPanel(
            User user,
            BorrowRequestController requestController,
            EquipmentController equipmentController,
            BorrowRequest existingRequest,
            Runnable onSaved) {
        this.borrowerId = user.getUserId();
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
        buildForm();
        loadEquipment();
    }

    private void initializeValues(User user) {
        LocalDate eventDay = LocalDate.now().plusDays(1);
        eventDate.setText(eventDay.format(DateTimeFormatter.ISO_LOCAL_DATE));
        startTime.setText("09:00");
        endTime.setText("10:00");
        participants.setText("1");
        personInCharge.setText(user.getFullName());
        position.setText("Borrower");
        purpose.setLineWrap(true);
        purpose.setWrapStyleWord(true);

        if (existingRequest != null) {
            venue.setText(existingRequest.getVenue());
            eventDate.setText(existingRequest.getEventDate().toString());
            startTime.setText(existingRequest.getStartTime().format(
                    DateTimeFormatter.ofPattern("HH:mm")));
            endTime.setText(existingRequest.getEndTime().format(
                    DateTimeFormatter.ofPattern("HH:mm")));
            participants.setText(existingRequest.getParticipants());
            personInCharge.setText(existingRequest.getPersonInCharge());
            position.setText(existingRequest.getPosition());
            purpose.setText(existingRequest.getPurpose());
            selectedItems.addAll(existingRequest.getEquipmentItems());
        }
    }

    private void buildForm() {
        JPanel introduction = new JPanel(new BorderLayout(8, 3));
        introduction.setOpaque(false);
        JLabel heading = new JLabel(existingRequest == null
                ? "Tell us what you need" : "Update your request");
        heading.setFont(AppTheme.semibold(18));
        heading.setForeground(AppTheme.foreground());
        JLabel hint = new JLabel("Complete the activity details, then add equipment from campus inventory.");
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
        addFormField(eventCard, "Venue", venue, 1, 0);
        addFormField(eventCard, "Event date (YYYY-MM-DD)", eventDate, 1, 1);
        addFormField(eventCard, "Start time (HH:MM)", startTime, 2, 0);
        addFormField(eventCard, "End time (HH:MM)", endTime, 2, 1);
        addFormField(eventCard, "Participants", participants, 3, 0);
        addFormField(eventCard, "Person in charge", personInCharge, 3, 1);
        addFormField(eventCard, "Position", position, 4, 0);
        addFormField(eventCard, "Purpose", new JScrollPane(purpose), 4, 1);
        eventCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(eventCard);
        add(Box.createVerticalStrut(14));

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

    private void addSectionTitle(JPanel panel, String text, int row) {
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

    private void addFormField(
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

    private void styleField(JComponent field) {
        field.setFont(AppTheme.regular(12));
        field.setForeground(AppTheme.foreground());
        field.setBackground(AppTheme.surface());
    }

    private void styleArea(JTextArea area) {
        area.setFont(AppTheme.regular(12));
        area.setForeground(AppTheme.foreground());
        area.setBackground(AppTheme.surface());
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
    }

    private void loadEquipment() {
        try {
            LocalDate date = LocalDate.parse(eventDate.getText().trim());
            LocalTime start = LocalTime.parse(startTime.getText().trim());
            LocalTime end = LocalTime.parse(endTime.getText().trim());
            loadEquipment(date, start, end);
        } catch (DateTimeParseException exception) {
            inventoryNote.setForeground(AppTheme.warning());
            inventoryNote.setText("Enter a valid date and time to check availability.");
            equipmentPicker.setEnabled(false);
        }
    }

    private void loadEquipment(LocalDate date, LocalTime start, LocalTime end) {
        inventoryNote.setForeground(AppTheme.muted());
        inventoryNote.setText("Checking availability for " + date + "…");
        equipmentPicker.setEnabled(false);
        new SwingWorker<List<Equipment>, Void>() {
            @Override
            protected List<Equipment> doInBackground() throws Exception {
                return equipmentController.findAvailableEquipmentForSchedule(
                        date, start, end,
                        existingRequest == null ? -1 : existingRequest.getRequestId());
            }

            @Override
            protected void done() {
                try {
                    availableEquipment.clear();
                    availableEquipment.addAll(get());
                    DefaultComboBoxModel<Equipment> model = new DefaultComboBoxModel<>();
                    for (Equipment item : availableEquipment) {
                        model.addElement(item);
                    }
                    equipmentPicker.setModel(model);
                    equipmentPicker.setEnabled(!availableEquipment.isEmpty());
                    inventoryNote.setText(availableEquipment.isEmpty()
                            ? "No available equipment is registered in the database."
                            : "Availability reflects active requests, damaged items, and lost items.");
                    renderSelectedItems();
                } catch (Exception exception) {
                    inventoryNote.setForeground(AppTheme.danger());
                    inventoryNote.setText("Unable to load equipment: " + exception.getMessage());
                    equipmentPicker.setEnabled(false);
                }
            }
        }.execute();
    }

    private void listenForScheduleChanges() {
        javax.swing.event.DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleAvailabilityRefresh();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleAvailabilityRefresh();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleAvailabilityRefresh();
            }
        };
        eventDate.getDocument().addDocumentListener(listener);
        startTime.getDocument().addDocumentListener(listener);
        endTime.getDocument().addDocumentListener(listener);
        availabilityTimer = new Timer(650, event -> loadEquipment());
        availabilityTimer.setRepeats(false);
    }

    private void scheduleAvailabilityRefresh() {
        if (availabilityTimer == null) {
            return;
        }
        equipmentPicker.setEnabled(false);
        inventoryNote.setForeground(AppTheme.muted());
        inventoryNote.setText("Updating availability for the selected schedule…");
        availabilityTimer.restart();
    }

    private void addSelectedItem() {
        Equipment selected = (Equipment) equipmentPicker.getSelectedItem();
        if (selected == null) {
            showMessage("No equipment is currently available.", "Equipment unavailable");
            return;
        }
        int requested = (Integer) quantity.getValue();
        if (requested > selected.getAvailableQuantity()) {
            showMessage("Only " + selected.getAvailableQuantity()
                    + " unit(s) are available for " + selected.getEquipmentName() + ".",
                    "Not enough equipment");
            return;
        }

        for (int i = 0; i < selectedItems.size(); i++) {
            if (selectedItems.get(i).getEquipmentId() == selected.getEquipmentId()) {
                selectedItems.set(i, createItem(selected, requested));
                renderSelectedItems();
                return;
            }
        }
        selectedItems.add(createItem(selected, requested));
        renderSelectedItems();
    }

    private EquipmentRequestItem createItem(Equipment equipment, int requested) {
        return new EquipmentRequestItem(
                equipment.getEquipmentId(),
                equipment.getEquipmentCode(),
                equipment.getEquipmentName(),
                equipment.getCategory(),
                requested,
                0, 0, 0);
    }

    private void renderSelectedItems() {
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

    private void saveRequest() {
        BorrowRequestForm form;
        try {
            form = new BorrowRequestForm(
                    venue.getText(),
                    LocalDate.parse(eventDate.getText().trim()),
                    LocalTime.parse(startTime.getText().trim()),
                    LocalTime.parse(endTime.getText().trim()),
                    purpose.getText(),
                    participants.getText(),
                    personInCharge.getText(),
                    position.getText(),
                    selectedItems);
        } catch (DateTimeParseException exception) {
            showMessage("Use YYYY-MM-DD for the date and HH:MM for times.",
                    "Check date and time");
            return;
        }

        saveButton.setEnabled(false);
        saveButton.setText(existingRequest == null ? "Submitting…" : "Saving…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (existingRequest == null) {
                    requestController.createRequest(borrowerId, form);
                } else {
                    requestController.updateRequest(
                            borrowerId, existingRequest.getRequestId(), form);
                }
                return null;
            }

            @Override
            protected void done() {
                saveButton.setEnabled(true);
                saveButton.setText(existingRequest == null
                        ? "Submit request" : "Save changes");
                try {
                    get();
                    showMessage(existingRequest == null
                                    ? "Your request has been submitted for review."
                                    : "Your request has been updated and returned to the review queue.",
                            "Request saved");
                    onSaved.run();
                } catch (Exception exception) {
                    showMessage(exception.getCause() == null
                                    ? exception.getMessage()
                                    : exception.getCause().getMessage(),
                            "Unable to save request");
                }
            }
        }.execute();
    }

    private void showMessage(String message, String title) {
        javax.swing.JOptionPane.showMessageDialog(
                this, message, title, javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private static class EquipmentRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focused);
            if (value instanceof Equipment) {
                Equipment item = (Equipment) value;
                label.setText(item.getEquipmentName() + "  ·  "
                        + item.getAvailableQuantity() + " available  ·  "
                        + item.getCategory());
            } else {
                label.setText("Choose available equipment");
            }
            label.setFont(AppTheme.regular(12));
            return label;
        }
    }
}
