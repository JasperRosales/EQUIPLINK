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

    private static final int REFRESH_MILLIS = 10_000;
    private static final String ALL_CATEGORIES = "All categories";
    private static final String[] QUANTITY_FILTERS = {
            "Any quantity", "In stock", "Low stock (1–5)", "6–20 available",
            "More than 20", "Out of stock" };

    private final User actor;
    private final EquipmentController equipmentController;
    private final java.util.function.Consumer<List<src.model.EquipmentRequestItem>> onRequest;
    private final JTextField search = new JTextField(22);
    private final JComboBox<String> categoryFilter = new JComboBox<>(
            new String[] { ALL_CATEGORIES });
    private final JComboBox<String> quantityFilter = new JComboBox<>(QUANTITY_FILTERS);
    private final JPanel inventoryList = new JPanel();
    private final JLabel updatedLabel = new JLabel(" ");
    private final JLabel selectionLabel = new JLabel();
    private final PrimaryButton requestSelected = new PrimaryButton("Request selected");
    private final java.util.Map<Long, Integer> selection = new java.util.LinkedHashMap<>();
    private final javax.swing.Timer refreshTimer =
            new javax.swing.Timer(REFRESH_MILLIS, event -> loadEquipment(true));
    private List<Equipment> equipmentItems = List.of();
    private String lastSnapshot = "";
    private boolean loading;

    public EquipmentInventoryPanel(EquipmentController equipmentController) {
        this(null, equipmentController);
    }

    public EquipmentInventoryPanel(User actor, EquipmentController equipmentController) {
        this(actor, equipmentController, null);
    }

    /**
     * @param onRequest when set (borrower view), items can be selected with quantities
     *                  and handed to the request form in one go
     */
    public EquipmentInventoryPanel(
            User actor,
            EquipmentController equipmentController,
            java.util.function.Consumer<List<src.model.EquipmentRequestItem>> onRequest) {
        this.actor = actor;
        this.equipmentController = equipmentController;
        this.onRequest = onRequest;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        addHeader();
        inventoryList.setOpaque(false);
        inventoryList.setLayout(new BoxLayout(inventoryList, BoxLayout.Y_AXIS));
        add(inventoryList);
        loadEquipment(false);
    }

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

    private boolean isCatalog() {
        return onRequest != null;
    }

    private void addHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 10));
        header.setOpaque(false);
        JPanel titleBar = new JPanel(new BorderLayout(12, 0));
        titleBar.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(isCatalog() ? "Available equipment" : "Inventory");
        heading.setFont(AppTheme.semibold(18));
        heading.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel(isCatalog()
                ? "Live stock on hand. Select several items and request them together."
                : "Stock on hand updates automatically. Units are deducted only when you "
                        + "confirm release after the campus director approves.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        text.add(heading);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        titleBar.add(text, BorderLayout.CENTER);
        updatedLabel.setFont(AppTheme.regular(10.5f));
        updatedLabel.setForeground(AppTheme.muted());
        JPanel updatedWrap = new JPanel(new java.awt.GridBagLayout());
        updatedWrap.setOpaque(false);
        updatedWrap.add(updatedLabel);
        titleBar.add(updatedWrap, BorderLayout.EAST);
        header.add(titleBar, BorderLayout.NORTH);

        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.setOpaque(false);
        UiSupport.styleInput(search);
        search.putClientProperty("JTextField.placeholderText", "Search by name, code, or category");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(UiSupport.documentListener(this::showEquipment));
        controls.add(search, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        categoryFilter.setFont(AppTheme.regular(12));
        categoryFilter.setPreferredSize(new java.awt.Dimension(170, 40));
        categoryFilter.setToolTipText("Filter by category");
        categoryFilter.addActionListener(event -> showEquipment());
        quantityFilter.setFont(AppTheme.regular(12));
        quantityFilter.setPreferredSize(new java.awt.Dimension(160, 40));
        quantityFilter.setToolTipText("Filter by quantity on hand");
        quantityFilter.addActionListener(event -> showEquipment());
        actions.add(categoryFilter);
        actions.add(quantityFilter);
        if (canManageInventory()) {
            PrimaryButton addButton = new PrimaryButton("Add equipment");
            addButton.addActionListener(event -> showEquipmentEditor(null));
            actions.add(addButton);
        }
        PrimaryButton refresh = new PrimaryButton("Refresh", PrimaryButton.Style.SECONDARY);
        refresh.addActionListener(event -> loadEquipment(false));
        actions.add(refresh);
        controls.add(actions, BorderLayout.EAST);
        header.add(controls, BorderLayout.CENTER);

        if (isCatalog()) {
            SurfacePanel selectionBar = new SurfacePanel();
            selectionBar.setLayout(new BorderLayout(12, 0));
            selectionBar.setBorder(new EmptyBorder(13, 18, 13, 14));
            selectionLabel.setFont(AppTheme.semibold(12));
            selectionLabel.setForeground(AppTheme.foreground());
            selectionBar.add(selectionLabel, BorderLayout.CENTER);
            PrimaryButton clear = new PrimaryButton("Clear", PrimaryButton.Style.SECONDARY);
            clear.addActionListener(event -> {
                selection.clear();
                updateSelection();
                showEquipment();
            });
            requestSelected.addActionListener(event -> requestSelection());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            buttons.add(clear);
            buttons.add(requestSelected);
            selectionBar.add(buttons, BorderLayout.EAST);
            header.add(selectionBar, BorderLayout.SOUTH);
            updateSelection();
        }
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(14));
    }

    /** Loads inventory; silent refreshes only redraw when stock actually changed. */
    private void loadEquipment(boolean silent) {
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

    private String snapshot(List<Equipment> items) {
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

    private void updateCategories() {
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
    private void pruneSelection() {
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

    private boolean matchesQuantity(Equipment item) {
        int available = "AVAILABLE".equals(item.getStatus()) ? item.getAvailableQuantity() : 0;
        switch (quantityFilter.getSelectedIndex()) {
            case 1:
                return available > 0;
            case 2:
                return available >= 1 && available <= 5;
            case 3:
                return available >= 6 && available <= 20;
            case 4:
                return available > 20;
            case 5:
                return available == 0;
            default:
                return true;
        }
    }

    private void showEquipment() {
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

    private JPanel equipmentCard(Equipment item) {
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
                g.setColor(AppTheme.alpha(new java.awt.Color(0, 0, 128),
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
            edit.addActionListener(event -> showEquipmentEditor(item));
            PrimaryButton delete = new PrimaryButton("Delete", PrimaryButton.Style.DANGER);
            delete.addActionListener(event -> deleteEquipment(item));
            actions.add(edit);
            actions.add(delete);
            card.add(actions, BorderLayout.SOUTH);
        }
        return card;
    }

    private JPanel selectionControls(Equipment item, int onHand) {
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

    private void updateSelection() {
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

    private void requestSelection() {
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
                    loadEquipment(false);
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
                    loadEquipment(false);
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
