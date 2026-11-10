
package src.ui.components;

import src.ui.AppTheme;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class NavigationButton extends JButton {

    private boolean hovered;
    private boolean active;

    public NavigationButton(String text) {

        super(text);

        setHorizontalAlignment(LEFT);

        setFont(AppTheme.regular(12));
        setForeground(new Color(218, 226, 238));

        setBorder(new EmptyBorder(10, 3, 10, 8));

        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);

        // Allow button to occupy the available sidebar width
        setMaximumSize(
                new java.awt.Dimension(Integer.MAX_VALUE, 43));

        // Keep the preferred width within the sidebar's inner width so
        // BoxLayout can stretch the button to fill it without overflowing.
        setPreferredSize(
                new java.awt.Dimension(200, 43));

        setAlignmentX(LEFT_ALIGNMENT);

        addMouseListener(new MouseAdapter() {

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
        });
    }

    public void setActive(boolean active) {

        this.active = active;

        setForeground(
                active
                        ? Color.WHITE
                        : new Color(195, 207, 225));

        setFont(
                active
                        ? AppTheme.semibold(12)
                        : AppTheme.regular(12));

        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {

        Graphics2D g = (Graphics2D) graphics.create();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Active navigation background
        if (active) {

            g.setColor(new Color(54, 75, 110));

            g.fillRoundRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    8,
                    8);

            // Active indicator
            g.setColor(AppTheme.primary());

            g.fillRoundRect(
                    0,
                    8,
                    3,
                    getHeight() - 16,
                    3,
                    3);

        } else if (hovered) {

            // Hover background
            g.setColor(new Color(255, 255, 255, 15));

            g.fillRoundRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    8,
                    8);
        }

        g.dispose();

        super.paintComponent(graphics);
    }
}
