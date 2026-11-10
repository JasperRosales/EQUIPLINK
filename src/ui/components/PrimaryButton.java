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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PrimaryButton extends JButton {

    public enum Style {
        PRIMARY, SECONDARY, DANGER
    }

    private final Style style;
    private boolean hovered;
    private boolean pressed;

    public PrimaryButton(String text) {
        this(text, Style.PRIMARY);
    }

    public PrimaryButton(String text, Style style) {
        super(text);
        this.style = style;
        setFont(AppTheme.semibold(12.5f));
        setForeground(style == Style.SECONDARY ? AppTheme.foreground() : Color.WHITE);
        setBorder(new EmptyBorder(9, 16, 9, 16));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                pressed = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int inset = 1;
        int arc = 10;
        Color base = baseColor();
        if (pressed) {
            base = base.darker();
        } else if (hovered && style != Style.SECONDARY) {
            base = darken(base);
        }

        g.setColor(isEnabled() ? base : AppTheme.border());
        g.fillRoundRect(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2, arc, arc);
        if (style == Style.SECONDARY) {
            g.setColor(AppTheme.border());
            g.drawRoundRect(inset, inset, getWidth() - inset * 2 - 1,
                    getHeight() - inset * 2 - 1, arc, arc);
        }
        if (hovered && style == Style.SECONDARY) {
            g.setColor(new Color(AppTheme.primary().getRed(), AppTheme.primary().getGreen(),
                    AppTheme.primary().getBlue(), 24));
            g.fillRoundRect(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2, arc, arc);
        }
        g.dispose();
        super.paintComponent(graphics);
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

    private Color darken(Color color) {
        return new Color(Math.max(0, color.getRed() - 18), Math.max(0, color.getGreen() - 18),
                Math.max(0, color.getBlue() - 18), color.getAlpha());
    }

    private Color brighten(Color color) {
        return new Color(
                Math.min(255, color.getRed() + 14),
                Math.min(255, color.getGreen() + 14),
                Math.min(255, color.getBlue() + 14),
                color.getAlpha());
    }
}
