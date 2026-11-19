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

/** Part 1 of EquipmentInventoryPanel, split out to keep files short. */
public abstract class EquipmentInventoryPanelPart1 extends JPanel {

    static final int REFRESH_MILLIS = 10_000;
    static final String ALL_CATEGORIES = "All categories";
    static final String QTY_ANY = "Any quantity";
    static final String QTY_NONE = "Unavailable (0 on hand)";
    static final String QTY_LOW = "Low stock (1–5)";
    static final String QTY_CATALOG_LOW = "1–5 available";
    static final String QTY_MID = "6–20 available";
    static final String QTY_HIGH = "More than 20";
    static final String[] STAFF_QUANTITY_FILTERS = {
            QTY_ANY, QTY_NONE, QTY_LOW, QTY_MID, QTY_HIGH };
    static final String[] CATALOG_QUANTITY_FILTERS = {
            QTY_ANY, QTY_CATALOG_LOW, QTY_MID, QTY_HIGH };

    User actor;
    EquipmentController equipmentController;
    java.util.function.Consumer<List<src.model.EquipmentRequestItem>> onRequest;
    final JTextField search = new JTextField(22);
    final JComboBox<String> categoryFilter = new JComboBox<>(
            new String[] { ALL_CATEGORIES });
    final JComboBox<String> quantityFilter = new JComboBox<>();
    final JPanel inventoryList = new JPanel();
    final JLabel updatedLabel = new JLabel(" ");
    final JLabel selectionLabel = new JLabel();
    final PrimaryButton requestSelected = new PrimaryButton("Request selected");
    final java.util.Map<Long, Integer> selection = new java.util.LinkedHashMap<>();
    javax.swing.Timer refreshTimer;

    List<Equipment> equipmentItems = List.of();
    String lastSnapshot = "";
    boolean loading;

    @Override
    public void addNotify() {
        super.addNotify();
        refreshTimer.start();
    }

    @Override
    public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }

    boolean isCatalog() {
        return onRequest != null;
    }

    String snapshot(List<Equipment> items) {
        StringBuilder value = new StringBuilder();
        for (Equipment item : items) {
            value.append(item.getEquipmentId()).append(':').append(item.getAvailableQuantity())
                    .append(':').append(item.getInUseQuantity()).append(':')
                    .append(item.getTotalQuantity()).append(':').append(item.getStatus())
                    .append(':').append(item.getEquipmentName()).append(':')
                    .append(item.getCategory()).append(';');
        }
        return value.toString();
    }

    void updateCategories() {
        Object selected = categoryFilter.getSelectedItem();
        java.util.Set<String> categories = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Equipment item : equipmentItems) {
            categories.add(item.getCategory());
        }
        java.awt.event.ActionListener[] listeners = categoryFilter.getActionListeners();
        for (java.awt.event.ActionListener listener : listeners) {
            categoryFilter.removeActionListener(listener);
        }
        categoryFilter.removeAllItems();
        categoryFilter.addItem(ALL_CATEGORIES);
        for (String category : categories) {
            categoryFilter.addItem(category);
        }
        categoryFilter.setSelectedItem(
                selected != null && categories.contains(selected) ? selected : ALL_CATEGORIES);
        for (java.awt.event.ActionListener listener : listeners) {
            categoryFilter.addActionListener(listener);
        }
    }

    /** Drops or caps selections when stock changes after a refresh. */
    void pruneSelection() {
        java.util.Map<Long, Integer> onHand = new java.util.HashMap<>();
        for (Equipment item : equipmentItems) {
            if ("AVAILABLE".equals(item.getStatus())) {
                onHand.put(item.getEquipmentId(), item.getAvailableQuantity());
            }
        }
        selection.entrySet().removeIf(entry -> onHand.getOrDefault(entry.getKey(), 0) == 0);
        selection.replaceAll((id, quantity) -> Math.min(quantity, onHand.get(id)));
        updateSelection();
    }

    boolean matchesQuantity(Equipment item) {
        int available = "AVAILABLE".equals(item.getStatus()) ? item.getAvailableQuantity() : 0;
        String selected = String.valueOf(quantityFilter.getSelectedItem());
        return switch (selected) {
            case QTY_NONE -> available == 0;
            case QTY_LOW, QTY_CATALOG_LOW -> available >= 1 && available <= 5;
            case QTY_MID -> available >= 6 && available <= 20;
            case QTY_HIGH -> available > 20;
            default -> true;
        };
    }

    JPanel selectionControls(Equipment item, int onHand) {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        long id = item.getEquipmentId();
        Integer chosen = selection.get(id);
        JSpinner quantity = new JSpinner(new SpinnerNumberModel(
                chosen == null ? 1 : chosen.intValue(), 1, Math.max(1, onHand), 1));
        quantity.setPreferredSize(new java.awt.Dimension(80, 36));
        javax.swing.JCheckBox select = new javax.swing.JCheckBox("Select");
        select.setOpaque(false);
        select.setFont(AppTheme.semibold(12));
        select.setSelected(chosen != null);
        select.setEnabled(onHand > 0);
        quantity.setEnabled(onHand > 0);
        select.addActionListener(event -> {
            if (select.isSelected()) {
                selection.put(id, (Integer) quantity.getValue());
            } else {
                selection.remove(id);
            }
            updateSelection();
        });
        quantity.addChangeListener(event -> {
            if (selection.containsKey(id)) {
                selection.put(id, (Integer) quantity.getValue());
                updateSelection();
            }
        });
        JLabel qtyLabel = new JLabel("Qty");
        qtyLabel.setFont(AppTheme.regular(12));
        qtyLabel.setForeground(AppTheme.muted());
        controls.add(qtyLabel);
        controls.add(quantity);
        controls.add(select);
        return controls;
    }

    void updateSelection() {
        int units = 0;
        for (int quantity : selection.values()) {
            units += quantity;
        }
        selectionLabel.setText(selection.isEmpty()
                ? "Select items and quantities, then request them together."
                : selection.size() + (selection.size() == 1 ? " item" : " items")
                        + " selected · " + units + (units == 1 ? " unit" : " units"));
        requestSelected.setEnabled(!selection.isEmpty());
        requestSelected.setText(selection.isEmpty()
                ? "Request selected" : "Request " + selection.size() + " selected");
    }

    void requestSelection() {
        if (selection.isEmpty()) {
            return;
        }
        List<src.model.EquipmentRequestItem> items = new ArrayList<>();
        for (Equipment item : equipmentItems) {
            Integer quantity = selection.get(item.getEquipmentId());
            if (quantity != null) {
                items.add(new src.model.EquipmentRequestItem(
                        item.getEquipmentId(), item.getEquipmentCode(),
                        item.getEquipmentName(), item.getCategory(), quantity, 0, 0, 0));
            }
        }
        onRequest.accept(items);
    }

    void styleEditorField(JTextField field) {
        field.setFont(AppTheme.regular(11));
        UiSupport.styleInput(field);
    }

    JLabel editorLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.semibold(10));
        label.setForeground(AppTheme.foreground());
        return label;
    }

    boolean canManageInventory() {
        return actor != null
                && ("ICT_STAFF".equalsIgnoreCase(actor.getRole())
                        || "FACILITY_STAFF".equalsIgnoreCase(actor.getRole()));
    }

    JPanel message(String text, boolean error) {
        SurfacePanel panel = new SurfacePanel();
        panel.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(11));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    void showMessage(String text, boolean error) {
        inventoryList.removeAll();
        inventoryList.add(message(text, error));
        inventoryList.revalidate();
        inventoryList.repaint();
    }

    String displayRole(String role) {
        if ("ICT_STAFF".equals(role)) {
            return "System Admin";
        }
        if ("FACILITY_STAFF".equals(role)) {
            return "Facility Staff";
        }
        return role == null ? "Unassigned" : role.replace('_', ' ');
    }

    String messageOf(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause() : exception;
        return cause.getMessage();
    }
}
