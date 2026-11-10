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
        setFont(AppTheme.semibold(13));
        setForeground(style == Style.SECONDARY ? AppTheme.foreground() : Color.WHITE);
        setBorder(new EmptyBorder(10, 17, 10, 17));
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
        int arc = 13;
        Color base = baseColor();
        if (pressed) {
            base = base.darker();
        } else if (hovered) {
            base = brighten(base);
        }

        if (style == Style.PRIMARY && !pressed) {
            g.setPaint(new GradientPaint(0, 0, brighten(base), getWidth(), getHeight(), base));
        } else {
            g.setColor(base);
        }
        g.fillRoundRect(inset, inset, getWidth() - inset * 2, getHeight() - inset * 2, arc, arc);
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
                return AppTheme.elevatedSurface();
            case DANGER:
                return AppTheme.danger();
            case PRIMARY:
            default:
                return AppTheme.primary();
        }
    }

    private Color brighten(Color color) {
        return new Color(
                Math.min(255, color.getRed() + 14),
                Math.min(255, color.getGreen() + 14),
                Math.min(255, color.getBlue() + 14),
                color.getAlpha());
    }
}
