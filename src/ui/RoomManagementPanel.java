package src.ui;

import src.model.User;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;
import src.ui.components.VenueOptions;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** ICT-only page that keeps the request-form Venue dropdown current. */
public class RoomManagementPanel extends JPanel {

    private final User actor;
    private final JTextField roomInput = new JTextField(24);
    private final JPanel roomGrid = new VenueGrid(new WrapLayout(FlowLayout.LEFT, 10, 10));
    private final JLabel roomCount = new JLabel("0 venues");

    public RoomManagementPanel(User actor) {
        this.actor = actor;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        addHeader();
        add(Box.createVerticalStrut(14));

        SurfacePanel listCard = new SurfacePanel();
        listCard.setLayout(new BorderLayout(10, 10));
        listCard.setAlignmentX(LEFT_ALIGNMENT);
        listCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 460));

        JPanel sectionHeading = new JPanel(new BorderLayout());
        sectionHeading.setOpaque(false);
        JLabel saved = new JLabel("Saved venues");
        saved.setFont(AppTheme.semibold(13));
        saved.setForeground(AppTheme.foreground());
        roomCount.setFont(AppTheme.regular(11));
        roomCount.setForeground(AppTheme.muted());
        roomCount.setHorizontalAlignment(SwingConstants.RIGHT);
        sectionHeading.add(saved, BorderLayout.WEST);
        sectionHeading.add(roomCount, BorderLayout.EAST);
        listCard.add(sectionHeading, BorderLayout.NORTH);

        roomGrid.setOpaque(false);
        roomGrid.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
        roomGrid.setAlignmentX(LEFT_ALIGNMENT);
        roomGrid.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                roomGrid.revalidate();
            }
        });

        JScrollPane scroller = new JScrollPane(roomGrid);
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(new EmptyBorder(2, 2, 2, 2));
        scroller.setPreferredSize(new Dimension(0, 340));
        scroller.getVerticalScrollBar().setUnitIncrement(18);
        listCard.add(scroller, BorderLayout.CENTER);
        add(listCard);
        reloadRooms();
    }

    private void addHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Venue management");
        title.setFont(AppTheme.semibold(18));
        title.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel(
                "Add rooms here to make them available immediately in the request-form Venue dropdown.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        header.add(text, BorderLayout.NORTH);

        UiSupport.styleInput(roomInput);
        roomInput.putClientProperty("JTextField.placeholderText", "Enter a room or venue name");
        PrimaryButton add = new PrimaryButton("Add room");
        add.addActionListener(event -> addRoom(add));
        roomInput.addActionListener(event -> addRoom(add));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(roomInput);
        controls.add(add);
        header.add(controls, BorderLayout.CENTER);
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
    }

    private void addRoom(javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        String room = roomInput.getText() == null ? "" : roomInput.getText().trim();
        if (room.isEmpty()) {
            UiSupport.showMessageDialog(this, "Enter a room or venue name.",
                    "Room required", JOptionPane.WARNING_MESSAGE);
            UiSupport.endAction(source);
            return;
        }
        try {
            boolean added = VenueOptions.addRoom(room, actor == null ? "" : actor.getRole());
            roomInput.setText("");
            reloadRooms();
            UiSupport.showMessageDialog(this,
                    added ? "Room added to the request-form Venue dropdown."
                            : "That room is already in the Venue dropdown.",
                    added ? "Room added" : "Room already exists",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            UiSupport.showMessageDialog(this,
                    "Unable to add room: " + exception.getMessage(), "Room not added",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            UiSupport.endAction(source);
        }
    }

    private void reloadRooms() {
        roomGrid.removeAll();
        java.util.List<String> rooms = VenueOptions.load();
        roomCount.setText(rooms.size() + (rooms.size() == 1 ? " venue" : " venues"));
        if (rooms.isEmpty()) {
            JLabel empty = new JLabel("No venues saved yet. Add one above.");
            empty.setFont(AppTheme.regular(12));
            empty.setForeground(AppTheme.muted());
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            roomGrid.add(empty);
        } else {
            int index = 1;
            for (String room : rooms) {
                roomGrid.add(venueChip(room, index++));
            }
        }
        roomGrid.revalidate();
        roomGrid.repaint();
    }

    private JPanel venueChip(String room, int index) {
        JPanel chip = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.elevatedSurface());
                g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
                g.setColor(AppTheme.border());
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                g.setColor(AppTheme.primary());
                g.fillRoundRect(0, 8, 5, getHeight() - 16, 5, 5);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        chip.setOpaque(false);
        chip.setBorder(new EmptyBorder(9, 13, 9, 16));
        chip.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chip.setToolTipText(room);

        JLabel badge = new JLabel(String.format("%02d", index));
        badge.setFont(AppTheme.semibold(11));
        badge.setForeground(AppTheme.primary());

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(room);
        name.setFont(AppTheme.semibold(12));
        name.setForeground(AppTheme.foreground());
        JLabel note = new JLabel("Available in request form");
        note.setFont(AppTheme.regular(10));
        note.setForeground(AppTheme.muted());
        text.add(name);
        text.add(Box.createVerticalStrut(2));
        text.add(note);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        PrimaryButton editBtn = new PrimaryButton("Edit", PrimaryButton.Style.SECONDARY);
        editBtn.setToolTipText("Edit room name");
        editBtn.addActionListener(e -> editRoom(room));
        PrimaryButton delBtn = new PrimaryButton("Delete", PrimaryButton.Style.DANGER);
        delBtn.setToolTipText("Delete this room");
        delBtn.addActionListener(e -> deleteRoom(room));
        actions.add(editBtn);
        actions.add(delBtn);

        chip.add(badge);
        chip.add(text);
        chip.add(actions);
        return chip;
    }

    private void editRoom(String oldName) {
        String newName = (String) javax.swing.JOptionPane.showInputDialog(this,
                "Edit room name:", "Edit Room", javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, oldName);
        if (newName == null) return; // cancelled
        newName = newName.trim();
        if (newName.isEmpty()) {
            UiSupport.showMessageDialog(this, "Room name cannot be empty.", "Invalid name", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            src.ui.components.VenueOptions.updateRoom(oldName, newName, actor == null ? "" : actor.getRole());
            reloadRooms();
            UiSupport.showMessageDialog(this, "Room updated. It will appear in the request form Venue dropdown.",
                    "Room updated", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            UiSupport.showMessageDialog(this, "Unable to edit room: " + ex.getMessage(), "Edit failed", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteRoom(String roomName) {
        int confirm = UiSupport.showConfirmDialog(this,
                "Delete room \"" + roomName + "\"? It will be removed from the Venue dropdown.",
                "Delete Room", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
        if (confirm != javax.swing.JOptionPane.YES_OPTION) return;
        try {
            boolean ok = src.ui.components.VenueOptions.deleteRoom(roomName, actor == null ? "" : actor.getRole());
            if (ok) {
                reloadRooms();
                UiSupport.showMessageDialog(this, "Room deleted.", "Room deleted", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            } else {
                UiSupport.showMessageDialog(this, "Room not found.", "Not found", javax.swing.JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
            UiSupport.showMessageDialog(this, "Unable to delete room: " + msg, "Delete failed", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Wrapping grid that fills the viewport width so chips flow instead of scrolling sideways. */
    private static final class VenueGrid extends JPanel implements javax.swing.Scrollable {

        VenueGrid(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(40, visible.height - 36);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** FlowLayout that wraps components onto multiple rows inside a scroll pane. */
    private static final class WrapLayout extends FlowLayout {

        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target);
        }

        private Dimension layoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                if (targetWidth == 0) {
                    targetWidth = Integer.MAX_VALUE;
                }
                Insets insets = target.getInsets();
                int horizontal = insets.left + insets.right + getHgap() * 2;
                int maxWidth = targetWidth - horizontal;
                Dimension size = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                for (Component component : target.getComponents()) {
                    if (!component.isVisible()) {
                        continue;
                    }
                    Dimension preferred = component.getPreferredSize();
                    if (rowWidth > 0 && rowWidth + preferred.width > maxWidth) {
                        size.width = Math.max(size.width, rowWidth);
                        size.height += rowHeight + getVgap();
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    rowWidth += preferred.width + getHgap();
                    rowHeight = Math.max(rowHeight, preferred.height);
                }
                size.width = Math.max(size.width, rowWidth - getHgap());
                size.height += rowHeight;
                size.width += horizontal;
                size.height += insets.top + insets.bottom;
                return size;
            }
        }
    }
}
