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

public class EquipmentInventoryPanel extends EquipmentInventoryPanelPart2 {

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
        quantityFilter.setModel(new javax.swing.DefaultComboBoxModel<>(
                isCatalog() ? CATALOG_QUANTITY_FILTERS : STAFF_QUANTITY_FILTERS));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        addHeader();
        inventoryList.setOpaque(false);
        inventoryList.setLayout(new BoxLayout(inventoryList, BoxLayout.Y_AXIS));
        add(inventoryList);
        loadEquipment(false);
    }

    void addHeader() {
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
            addButton.addActionListener(event -> showEquipmentEditor(null, addButton));
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
    // Field initializers that call methods run here, after every part exists.
    {
        refreshTimer = new javax.swing.Timer(REFRESH_MILLIS, event -> loadEquipment(true));
    }
}
