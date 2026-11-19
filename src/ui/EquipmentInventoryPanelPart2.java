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

/** Part 2 of EquipmentInventoryPanel, split out to keep files short. */
public abstract class EquipmentInventoryPanelPart2 extends EquipmentInventoryPanelPart1 {

    /** Loads inventory; silent refreshes only redraw when stock actually changed. */
    void loadEquipment(boolean silent) {
        if (loading) {
            return;
        }
        loading = true;
        if (!silent) {
            showMessage("Loading inventory…", false);
        }
        new SwingWorker<List<Equipment>, Void>() {
            @Override
            protected List<Equipment> doInBackground() throws Exception {
                return canManageInventory()
                        ? equipmentController.findEquipmentForCustodian(actor)
                        : equipmentController.findAllEquipment();
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    List<Equipment> loaded = get();
                    String snapshot = snapshot(loaded);
                    updatedLabel.setText("Updated " + java.time.LocalTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("h:mm:ss a"))
                            + " · auto-refreshes");
                    if (silent && snapshot.equals(lastSnapshot)) {
                        return;
                    }
                    lastSnapshot = snapshot;
                    equipmentItems = loaded;
                    updateCategories();
                    pruneSelection();
                    showEquipment();
                } catch (Exception exception) {
                    if (!silent) {
                        showMessage("Unable to load equipment: " + messageOf(exception), true);
                    }
                }
            }
        }.execute();
    }

    void showEquipment() {
        inventoryList.removeAll();
        String query = search.getText().trim().toLowerCase();
        Object category = categoryFilter.getSelectedItem();
        List<Equipment> filtered = new ArrayList<>();
        for (Equipment item : equipmentItems) {
            String searchable = item.getEquipmentCode() + " "
                    + item.getEquipmentName() + " " + item.getCategory();
            boolean categoryMatch = category == null || ALL_CATEGORIES.equals(category)
                    || category.toString().equalsIgnoreCase(item.getCategory());
            if (categoryMatch && matchesQuantity(item)
                    && searchable.toLowerCase().contains(query)) {
                filtered.add(item);
            }
        }
        if (filtered.isEmpty()) {
            inventoryList.add(message(
                    equipmentItems.isEmpty()
                            ? "No equipment has been registered yet."
                            : "No equipment matches the current search or filters.",
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

    JPanel equipmentCard(Equipment item) {
        final float[] hover = {0f};
        SurfacePanel card = new SurfacePanel() {
            @Override
            protected void paintBorder(java.awt.Graphics graphics) {
                super.paintBorder(graphics);
                if (hover[0] <= 0f) {
                    return;
                }
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.alpha(AppTheme.PRIMARY,
                        Math.round(255 * hover[0])));
                g.setStroke(new java.awt.BasicStroke(2f));
                int m = SurfacePanel.MARGIN;
                g.drawRoundRect(m, m, getWidth() - m * 2 - 1, getHeight() - m * 2 - 1, 18, 18);
                g.dispose();
            }
        };
        card.setLayout(new BorderLayout(14, 8));
        javax.swing.Timer timer = new javax.swing.Timer(15, null);
        timer.addActionListener(event -> {
            boolean inside = card.getMousePosition() != null;
            float target = inside ? 1f : 0f;
            float step = 15f / 200f;
            if (Math.abs(hover[0] - target) <= step) {
                hover[0] = target;
                timer.stop();
            } else {
                hover[0] += hover[0] < target ? step : -step;
            }
            card.repaint();
        });
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                if (!timer.isRunning()) timer.start();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                if (!timer.isRunning()) timer.start();
            }
        });
        boolean usable = "AVAILABLE".equals(item.getStatus());
        int onHand = usable ? item.getAvailableQuantity() : 0;

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(item.getEquipmentName());
        name.setFont(AppTheme.semibold(14));
        name.setForeground(AppTheme.foreground());
        JLabel meta = new JLabel(item.getEquipmentCode() + "  ·  "
                + item.getCategory() + "  ·  " + displayRole(item.getCustodianRole()));
        meta.setFont(AppTheme.regular(11));
        meta.setForeground(AppTheme.muted());
        info.add(name);
        info.add(Box.createVerticalStrut(4));
        info.add(meta);
        if (!isCatalog()) {
            JLabel counts = new JLabel("Total " + item.getTotalQuantity()
                    + "  ·  In use " + item.getInUseQuantity()
                    + "  ·  Damaged " + item.getDamagedQuantity()
                    + "  ·  Lost " + item.getLostQuantity());
            counts.setFont(AppTheme.regular(11));
            counts.setForeground(AppTheme.muted());
            info.add(Box.createVerticalStrut(3));
            info.add(counts);
        }
        card.add(info, BorderLayout.CENTER);

        JPanel right = new JPanel(new GridLayout(0, 1, 0, 4));
        right.setOpaque(false);
        JLabel availability = new JLabel(onHand + " on hand", javax.swing.SwingConstants.RIGHT);
        availability.setFont(AppTheme.semibold(14));
        availability.setForeground(onHand == 0 ? AppTheme.danger()
                : onHand <= 5 ? AppTheme.warning() : AppTheme.success());
        right.add(availability);
        JLabel status = new src.ui.components.StatusBadge(
                usable ? (onHand == 0 ? "Out of stock" : onHand <= 5 ? "Low stock" : "In stock")
                        : item.getStatus().replace('_', ' ').toLowerCase(),
                usable ? (onHand == 0 ? AppTheme.danger()
                        : onHand <= 5 ? AppTheme.warning() : AppTheme.success())
                        : AppTheme.muted());
        status.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        right.add(status);
        card.add(right, BorderLayout.EAST);

        if (isCatalog()) {
            card.add(selectionControls(item, onHand), BorderLayout.SOUTH);
        } else if (canManageInventory()) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
            actions.setOpaque(false);
            PrimaryButton edit = new PrimaryButton("Edit", PrimaryButton.Style.SECONDARY);
            edit.addActionListener(event -> showEquipmentEditor(item, edit));
            PrimaryButton delete = new PrimaryButton("Delete", PrimaryButton.Style.DANGER);
            delete.addActionListener(event -> deleteEquipment(item, delete));
            actions.add(edit);
            actions.add(delete);
            card.add(actions, BorderLayout.SOUTH);
        }
        return card;
    }

    void showEquipmentEditor(Equipment equipment, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
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

        int answer = UiSupport.showConfirmDialog(
                this, form,
                equipment == null ? "Add equipment" : "Update equipment",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            UiSupport.endAction(source);
            return;
        }
        long quantityValue = ((Number) quantity.getValue()).longValue();
        if (quantityValue > Integer.MAX_VALUE) {
            UiSupport.endAction(source);
            UiSupport.showMessageDialog(
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
                    loadEquipment(false);
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(
                            EquipmentInventoryPanelPart2.this,
                            "Unable to save equipment: " + messageOf(exception),
                            "Inventory update failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }

    void deleteEquipment(Equipment equipment, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        int answer = UiSupport.showConfirmDialog(
                this,
                "Remove " + equipment.getEquipmentName()
                        + " from future requests? Existing request history will be retained.",
                "Delete equipment", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            UiSupport.endAction(source);
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
                    loadEquipment(false);
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(
                            EquipmentInventoryPanelPart2.this,
                            "Unable to delete equipment: " + messageOf(exception),
                            "Inventory update failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }
}
