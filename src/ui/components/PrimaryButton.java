package src.ui.components;

import src.ui.AppTheme;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class PrimaryButton extends JButton {

    public enum Style {
        PRIMARY, SECONDARY, DANGER
    }

    private static final int MARGIN = 3;
    private static final int ARC = 14;

    private final Style style;
    private final Tween hover = new Tween(this, 150);
    private final Tween press = new Tween(this, 90);

    public PrimaryButton(String text) {
        this(text, Style.PRIMARY);
    }

    public PrimaryButton(String text, Style style) {
        super(text);
        this.style = style;
        setFont(AppTheme.semibold(12.5f));
        setForeground(style == Style.SECONDARY ? AppTheme.foreground() : Color.WHITE);
        setBorder(new EmptyBorder(10, 18, 10, 18));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getModel().addChangeListener(event -> {
            hover.to(getModel().isRollover() && isEnabled() ? 1f : 0f);
            press.to(getModel().isArmed() && getModel().isPressed() ? 1f : 0f);
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Neu.antialias(g);
        int x = MARGIN;
        int y = MARGIN;
        int w = getWidth() - MARGIN * 2;
        int h = getHeight() - MARGIN * 2;
        float hv = hover.value();
        float pr = press.value();

        if (!isEnabled()) {
            g.setColor(AppTheme.border());
            g.fillRoundRect(x, y, w, h, ARC, ARC);
        } else if (style == Style.SECONDARY) {
            if (pr > 0.4f) {
                Neu.inset(g, x, y, w, h, ARC, 2.6f * pr, AppTheme.surface());
            } else {
                Neu.raised(g, x, y, w, h, ARC, 1.6f + 0.8f * hv, AppTheme.surface());
            }
            if (hv > 0f) {
                g.setColor(AppTheme.alpha(AppTheme.primary(), Math.round(18 * hv)));
                g.fillRoundRect(x, y, w, h, ARC, ARC);
            }
        } else {
            Color base = baseColor();
            Color top = AppTheme.mix(base, Color.WHITE, 0.16f + 0.06f * hv);
            Color bottom = AppTheme.mix(base, Color.BLACK, 0.06f + 0.10f * pr);
            float depth = (1.6f + 1.0f * hv) * (1f - pr);
            Neu.raised(g, x, y, w, h, ARC, depth, base);
            g.setPaint(new GradientPaint(0, y, top, 0, y + h, bottom));
            g.fillRoundRect(x, y, w, h, ARC, ARC);
            // Soft gloss along the top edge.
            g.setColor(AppTheme.alpha(Color.WHITE, Math.round(40 + 25 * hv)));
            g.drawRoundRect(x, y, w - 1, h - 1, ARC, ARC);
        }
        g.dispose();
        if (pr > 0f) {
            Graphics2D text = (Graphics2D) graphics.create();
            text.translate(0, Math.round(pr));
            super.paintComponent(text);
            text.dispose();
        } else {
            super.paintComponent(graphics);
        }
    }

    private Color baseColor() {
        switch (style) {
            case SECONDARY:
                return AppTheme.surface();
            case DANGER:
                return AppTheme.danger();
            case PRIMARY:
            default:
                return AppTheme.primary();
        }
    }
}
