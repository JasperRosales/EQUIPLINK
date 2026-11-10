package src.ui.components;

import src.ui.AppTheme;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Sidebar entry: soft raised on hover, pressed-in with an accent bar when active. */
public class NavigationButton extends JButton {

    private static final int ARC = 14;
    private final Tween hover = new Tween(this, 150);
    private final Tween activeTween = new Tween(this, 220);
    private boolean active;

    public NavigationButton(String text) {
        super(text);
        setHorizontalAlignment(LEFT);
        setFont(AppTheme.regular(12.5f));
        setForeground(AppTheme.sidebarText());
        setBorder(new EmptyBorder(9, 18, 9, 10));
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setRolloverEnabled(true);
        // Fill the sidebar width; keep preferred width within the sidebar's inner width.
        setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 42));
        setPreferredSize(new java.awt.Dimension(200, 42));
        setAlignmentX(LEFT_ALIGNMENT);
        getModel().addChangeListener(event -> hover.to(getModel().isRollover() ? 1f : 0f));
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        setForeground(active ? java.awt.Color.WHITE : AppTheme.sidebarText());
        setFont(active ? AppTheme.semibold(12.5f) : AppTheme.regular(12.5f));
        activeTween.to(active ? 1f : 0f);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Neu.antialias(g);
        float a = activeTween.value();
        float h = hover.value() * (1f - a);
        int m = 3;
        int w = getWidth() - m * 2;
        int ht = getHeight() - m * 2;
        if (a > 0.01f) {
            Neu.inset(g, m, m, w, ht, ARC, 3f * a, AppTheme.sidebar(),
                    AppTheme.sidebarShadowLight(), AppTheme.sidebarShadowDark());
            g.setColor(AppTheme.alpha(AppTheme.primary(), Math.round(40 * a)));
            g.fillRoundRect(m, m, w, ht, ARC, ARC);
            int bar = Math.round((ht - 16) * a);
            g.setColor(AppTheme.primary());
            g.fillRoundRect(m + 6, m + (ht - bar) / 2, 3, bar, 3, 3);
        }
        if (h > 0.01f) {
            Neu.raised(g, m, m, w, ht, ARC, 2f * h, AppTheme.mix(AppTheme.sidebar(),
                    AppTheme.sidebarShadowLight(), 0.35f * h),
                    AppTheme.alpha(AppTheme.sidebarShadowLight(), Math.round(255 * h)),
                    AppTheme.alpha(AppTheme.sidebarShadowDark(), Math.round(255 * h)));
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
