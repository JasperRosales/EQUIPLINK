package src.ui.components;

import src.ui.AppTheme;
import src.ui.UiSupport;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Rounded, tinted pill that displays a request or item status. */
public class StatusBadge extends JLabel {

    private final Color tone;

    public StatusBadge(String status) {
        this(UiSupport.displayStatus(status), UiSupport.statusColor(status));
    }

    /** Badge with custom text and color, e.g. a priority marker. */
    public StatusBadge(String text, Color tone) {
        super(text, SwingConstants.CENTER);
        this.tone = tone;
        setFont(AppTheme.semibold(10.5f));
        setForeground(tone);
        setBorder(new EmptyBorder(3, 10, 3, 10));
        setOpaque(false);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Dimension size = getPreferredSize();
        int w = Math.min(size.width, getWidth());
        int h = Math.min(size.height, getHeight());
        int x = getHorizontalAlignment() == RIGHT ? getWidth() - w
                : getHorizontalAlignment() == LEFT ? 0 : (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        g.setColor(new Color(tone.getRed(), tone.getGreen(), tone.getBlue(),
                AppTheme.isDarkMode() ? 48 : 26));
        g.fillRoundRect(x, y, w, h, h, h);
        g.dispose();
        super.paintComponent(graphics);
    }
}
