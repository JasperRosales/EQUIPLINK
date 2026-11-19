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

/** Part 2 of EquipmentRequestPanel, split out to keep files short. */
public abstract class EquipmentRequestPanelPart2 extends EquipmentRequestPanelPart1 {

    void loadEquipment() {
        try {
            LocalDate date = LocalDate.parse(eventDate.getText().trim());
            LocalTime start = parseTime(startTime.getText());
            LocalTime end = parseTime(endTime.getText());
            loadEquipment(date, start, end);
        } catch (DateTimeParseException exception) {
            inventoryNote.setForeground(AppTheme.warning());
            inventoryNote.setText("Enter a valid date and time (hh:mm AM/PM) to check availability.");
            equipmentPicker.setEnabled(false);
        }
    }

    void loadEquipment(LocalDate date, LocalTime start, LocalTime end) {
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
                            : "Shows stock on hand. Add as many items as you need; stock is deducted when staff confirm release after final approval.");
                    renderSelectedItems();
                } catch (Exception exception) {
                    inventoryNote.setForeground(AppTheme.danger());
                    inventoryNote.setText("Unable to load equipment: " + exception.getMessage());
                    equipmentPicker.setEnabled(false);
                }
            }
        }.execute();
    }

    void listenForScheduleChanges() {
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
        startTime.addDocumentListener(listener);
        endTime.addDocumentListener(listener);
        availabilityTimer = new Timer(650, event -> loadEquipment());
        availabilityTimer.setRepeats(false);
    }

    void addSelectedItem() {
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

    void addScheduleFromFields() {
        try {
            String v = venueText().toUpperCase(Locale.ROOT);
            LocalDate d = LocalDate.parse(eventDate.getText().trim());
            LocalTime st = parseTime(startTime.getText());
            LocalTime et = parseTime(endTime.getText());
            if (v.isBlank()) throw new IllegalArgumentException("Venue is required.");
            scheduleRows.add(new ScheduleRow(d, st, et, v));
            renderSchedules();
        } catch (Exception ex) {
            showMessage("Enter a valid venue/date/time, then click Add schedule.", "Check schedule");
        }
    }

    void saveRequest() {
        BorrowRequestForm form;
        try {
            String v = venueText().toUpperCase(Locale.ROOT);
            LocalDate d = LocalDate.parse(eventDate.getText().trim());
            LocalTime st = parseTime(startTime.getText());
            LocalTime et = parseTime(endTime.getText());
            List<RequestOccurrence> occs = new ArrayList<>();
            if (facultyMonthly) {
                if (scheduleRows.isEmpty()) {
                    showMessage("Add at least one schedule for this monthly faculty request.",
                            "Missing schedule");
                    return;
                }
                for (ScheduleRow r : scheduleRows) occs.add(r.toOccurrence());
            } else {
                occs.add(new RequestOccurrence(d, st, et, v));
            }
            RequestOccurrence first = occs.get(0);
            form = new BorrowRequestForm(
                    first.getVenue(),
                    first.getEventDate(),
                    first.getStartTime(),
                    first.getEndTime(),
                    purpose.getText(),
                    participants.getText(),
                    personInCharge.getText(),
                    position.getText(),
                    selectedItems,
                    occs);
        } catch (DateTimeParseException exception) {
            showMessage("Use YYYY-MM-DD for the date, hh:mm for times (e.g. 09:00), and pick AM or PM.",
                    "Check date and time");
            return;
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), "Check schedule");
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

    void showMessage(String message, String title) {
        UiSupport.showMessageDialog(
                this, message, title, javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    static class EquipmentRenderer extends DefaultListCellRenderer {
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
