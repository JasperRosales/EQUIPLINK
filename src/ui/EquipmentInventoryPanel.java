package src.ui;

import src.controller.EquipmentController;
import src.model.Equipment;
import src.model.User;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class EquipmentInventoryPanel extends JPanel {

    private final User actor;
    private final EquipmentController equipmentController;
    private final JTextField search = new JTextField(22);
    private final JPanel inventoryList = new JPanel();
    private List<Equipment> equipmentItems = List.of();

    public EquipmentInventoryPanel(EquipmentController equipmentController) {
        this(null, equipmentController);
    }

    public EquipmentInventoryPanel(User actor, EquipmentController equipmentController) {
        this.actor = actor;
        this.equipmentController = equipmentController;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        addHeader();
        inventoryList.setOpaque(false);
        inventoryList.setLayout(new BoxLayout(inventoryList, BoxLayout.Y_AXIS));
        add(inventoryList);
        loadEquipment();
    }

    private void addHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Equipment");
        heading.setFont(AppTheme.semibold(18));
        heading.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel("Search stock and availability in your equipment inventory.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        text.add(heading);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        header.add(text, BorderLayout.NORTH);

        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.setOpaque(false);
        UiSupport.styleInput(search);
        search.getDocument().addDocumentListener(UiSupport.documentListener(this::showEquipment));
        controls.add(search, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        if (canManageInventory()) {
            PrimaryButton addButton = new PrimaryButton("Add equipment");
            addButton.addActionListener(event -> showEquipmentEditor(null));
            actions.add(addButton);
        }
        PrimaryButton refresh = new PrimaryButton("Refresh", PrimaryButton.Style.SECONDARY);
        refresh.addActionListener(event -> loadEquipment());
        actions.add(refresh);
        controls.add(actions, BorderLayout.EAST);
        header.add(controls, BorderLayout.CENTER);
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(14));
    }

    private void loadEquipment() {
        showMessage("Loading inventory…", false);
        new SwingWorker<List<Equipment>, Void>() {
            @Override
            protected List<Equipment> doInBackground() throws Exception {
                return canManageInventory()
                        ? equipmentController.findEquipmentForCustodian(actor)
                        : equipmentController.findAllEquipment();
            }

            @Override
            protected void done() {
                try {
                    equipmentItems = get();
                    showEquipment();
                } catch (Exception exception) {
                    showMessage(
                            "Unable to load equipment: " + messageOf(exception), true);
                }
            }
        }.execute();
    }

    private void showEquipment() {
        inventoryList.removeAll();
        String query = search.getText().trim().toLowerCase();
        List<Equipment> filtered = new ArrayList<>();
        for (Equipment item : equipmentItems) {
            String searchable = item.getEquipmentCode() + " "
                    + item.getEquipmentName() + " " + item.getCategory();
            if (searchable.toLowerCase().contains(query)) {
                filtered.add(item);
            }
        }
        if (filtered.isEmpty()) {
            inventoryList.add(message(
                    equipmentItems.isEmpty()
                            ? "No equipment has been registered yet."
                            : "No equipment matches your search.",
                    false));
        } else {
            for (Equipment item : filtered) {
                inventoryList.add(equipmentCard(item));
                inventoryList.add(Box.createVerticalStrut(9));
            }
        }
        inventoryList.revalidate();
        inventoryList.repaint();
    }

    private JPanel equipmentCard(Equipment item) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout(14, 8));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(item.getEquipmentName());
        name.setFont(AppTheme.semibold(14));
        name.setForeground(AppTheme.foreground());
        JLabel meta = new JLabel(item.getEquipmentCode() + "  ·  "
                + item.getCategory() + "  ·  " + displayRole(item.getCustodianRole()));
        meta.setFont(AppTheme.regular(10));
        meta.setForeground(AppTheme.muted());
        info.add(name);
        info.add(Box.createVerticalStrut(4));
        info.add(meta);
        card.add(info, BorderLayout.CENTER);

        JPanel right = new JPanel(new GridLayout(0, 1, 0, 4));
        right.setOpaque(false);
        JLabel availability = new JLabel(
                item.getAvailableQuantity() + " / " + item.getTotalQuantity() + " available");
        availability.setFont(AppTheme.semibold(11));
        availability.setForeground(item.getAvailableQuantity() > 0
                ? AppTheme.success() : AppTheme.warning());
        JLabel condition = new JLabel(
                "Damaged " + item.getDamagedQuantity() + "  ·  Lost " + item.getLostQuantity());
        condition.setFont(AppTheme.regular(10.5f));
        condition.setForeground(AppTheme.muted());
        JLabel status = new JLabel(item.getStatus().replace('_', ' '));
        status.setFont(AppTheme.semibold(10.5f));
        status.setForeground("AVAILABLE".equals(item.getStatus())
                ? AppTheme.success() : AppTheme.warning());
        right.add(availability);
        right.add(condition);
        right.add(status);
        card.add(right, BorderLayout.EAST);

        if (canManageInventory()) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
            actions.setOpaque(false);
            PrimaryButton edit = new PrimaryButton("Edit", PrimaryButton.Style.SECONDARY);
            edit.addActionListener(event -> showEquipmentEditor(item));
            PrimaryButton delete = new PrimaryButton("Delete", PrimaryButton.Style.DANGER);
            delete.addActionListener(event -> deleteEquipment(item));
            actions.add(edit);
            actions.add(delete);
            card.add(actions, BorderLayout.SOUTH);
        }
        return card;
    }

    private void showEquipmentEditor(Equipment equipment) {
        JTextField code = new JTextField(equipment == null ? "" : equipment.getEquipmentCode());
        JTextField name = new JTextField(equipment == null ? "" : equipment.getEquipmentName());
        JTextField category = new JTextField(equipment == null ? "" : equipment.getCategory());
        int initialQuantity = equipment == null ? 1 : equipment.getTotalQuantity();
        JSpinner quantity = new JSpinner(
                new SpinnerNumberModel(initialQuantity, 0, 100000, 1));
        JComboBox<String> status = new JComboBox<>(
                new String[] { "AVAILABLE", "UNDER_MAINTENANCE", "RETIRED" });
        if (equipment != null) {
            status.setSelectedItem(equipment.getStatus());
        }
        styleEditorField(code);
        styleEditorField(name);
        styleEditorField(category);
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 9));
        form.setOpaque(false);
        form.add(editorLabel("Equipment code"));
        form.add(code);
        form.add(editorLabel("Equipment name"));
        form.add(name);
        form.add(editorLabel("Category"));
        form.add(category);
        form.add(editorLabel("Total quantity"));
        form.add(quantity);
        form.add(editorLabel("Status"));
        form.add(status);

        int answer = JOptionPane.showConfirmDialog(
                this, form,
                equipment == null ? "Add equipment" : "Update equipment",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        long quantityValue = ((Number) quantity.getValue()).longValue();
        if (quantityValue > Integer.MAX_VALUE) {
            JOptionPane.showMessageDialog(
                    this, "Quantity is too large.", "Invalid quantity",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                equipmentController.saveEquipment(
                        actor,
                        equipment == null ? null : equipment.getEquipmentId(),
                        code.getText(),
                        name.getText(),
                        category.getText(),
                        (int) quantityValue,
                        (String) status.getSelectedItem());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadEquipment();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(
                            EquipmentInventoryPanel.this,
                            "Unable to save equipment: " + messageOf(exception),
                            "Inventory update failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void deleteEquipment(Equipment equipment) {
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Remove " + equipment.getEquipmentName()
                        + " from future requests? Existing request history will be retained.",
                "Delete equipment", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                equipmentController.deleteEquipment(actor, equipment.getEquipmentId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadEquipment();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(
                            EquipmentInventoryPanel.this,
                            "Unable to delete equipment: " + messageOf(exception),
                            "Inventory update failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void styleEditorField(JTextField field) {
        field.setFont(AppTheme.regular(11));
        UiSupport.styleInput(field);
    }

    private JLabel editorLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.semibold(10));
        label.setForeground(AppTheme.foreground());
        return label;
    }

    private boolean canManageInventory() {
        return actor != null
                && ("ICT_STAFF".equalsIgnoreCase(actor.getRole())
                        || "FACILITY_STAFF".equalsIgnoreCase(actor.getRole()));
    }

    private JPanel message(String text, boolean error) {
        SurfacePanel panel = new SurfacePanel();
        panel.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(11));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private void showMessage(String text, boolean error) {
        inventoryList.removeAll();
        inventoryList.add(message(text, error));
        inventoryList.revalidate();
        inventoryList.repaint();
    }

    private String displayRole(String role) {
        if ("ICT_STAFF".equals(role)) {
            return "System Admin";
        }
        if ("FACILITY_STAFF".equals(role)) {
            return "Facility Staff";
        }
        return role == null ? "Unassigned" : role.replace('_', ' ');
    }

    private String messageOf(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause() : exception;
        return cause.getMessage();
    }
}
