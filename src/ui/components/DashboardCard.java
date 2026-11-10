package src.ui.components;

import src.ui.AppTheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DashboardCard extends JPanel {

    private final Color accent;
    private final JLabel amount;
    private boolean hovered;
    private Runnable clickAction;

    public DashboardCard(String title, String value, String caption, Color accent) {
        this.accent = accent;
        setLayout(new BorderLayout(10, 12));
        setOpaque(false);
        setBorder(new EmptyBorder(18, 19, 17, 19));

        JLabel label = new JLabel(title);
        label.setFont(AppTheme.regular(12));
        label.setForeground(AppTheme.muted());

        amount = new JLabel(value);
        amount.setFont(AppTheme.semibold(28));
        amount.setForeground(AppTheme.foreground());
        JLabel note = new JLabel(caption);
        note.setFont(AppTheme.regular(11));
        note.setForeground(AppTheme.muted());

        JPanel lower = new JPanel(new BorderLayout(0, 3));
        lower.setOpaque(false);
        lower.add(amount, BorderLayout.NORTH);
        lower.add(note, BorderLayout.CENTER);

        add(label, BorderLayout.NORTH);
        add(lower, BorderLayout.CENTER);
        installMouseHandlers(this);
    }

    public void setValue(String value) {
        amount.setText(value);
    }

    public void setClickAction(Runnable clickAction) {
        this.clickAction = clickAction;
        setCursor(Cursor.getPredefinedCursor(
                clickAction == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
    }

    private void installMouseHandlers(Component component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (clickAction != null) {
                    clickAction.run();
                }
            }
        });
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                installMouseHandlers(child);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(AppTheme.surface());
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        if (hovered) {
            g.setColor(withAlpha(accent, 12));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        }
        g.setColor(AppTheme.border());
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g.dispose();
        super.paintComponent(graphics);
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
