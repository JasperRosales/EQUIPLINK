package src.ui.components;

import src.ui.AppTheme;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class SurfacePanel extends JPanel {

    public SurfacePanel() {
        setOpaque(false);
        setBorder(new EmptyBorder(20, 20, 20, 20));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(15, 23, 42, AppTheme.isDarkMode() ? 0 : 10));
        g.fillRoundRect(0, 1, getWidth(), getHeight() - 1, 12, 12);
        g.setColor(AppTheme.surface());
        g.fillRoundRect(0, 0, getWidth(), getHeight() - 1, 12, 12);
        g.setColor(AppTheme.border());
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 2, 12, 12);
        g.dispose();
        super.paintComponent(graphics);
    }
}
