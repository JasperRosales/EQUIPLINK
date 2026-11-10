package src.ui.components;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import src.ui.AppTheme;

/** Rounded badge showing initials (user avatar or app logo). */
public class Avatar extends JLabel {

    private final Color fill;
    private final int arc;

    public Avatar(String text, Color fill, int arc) {
        super(text, SwingConstants.CENTER);
        this.fill = fill;
        this.arc = arc;
        setFont(AppTheme.semibold(11));
        setForeground(Color.WHITE);
        setOpaque(false);
        setPreferredSize(new Dimension(34, 34));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fill);
        int r = Math.min(arc, Math.min(getWidth(), getHeight()));
        g.fillRoundRect(0, 0, getWidth(), getHeight(), r, r);
        g.dispose();
        super.paintComponent(graphics);
    }
}
