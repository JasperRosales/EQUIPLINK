package src.ui.components;

import src.ui.AppTheme;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

/** Painting helpers for soft (neumorphic) raised and inset surfaces. */
public final class Neu {

    private Neu() {
    }

    public static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Raised surface using the page palette. */
    public static void raised(Graphics2D g, int x, int y, int w, int h, int arc, float depth,
            Color fill) {
        raised(g, x, y, w, h, arc, depth, fill, AppTheme.shadowLight(), AppTheme.shadowDark());
    }

    /**
     * Paints a soft raised rounded rect. {@code depth} is the shadow distance in px;
     * the shadow is blurred over roughly {@code 2 * depth} px.
     */
    public static void raised(Graphics2D g, int x, int y, int w, int h, int arc, float depth,
            Color fill, Color light, Color dark) {
        if (w <= 0 || h <= 0) {
            return;
        }
        antialias(g);
        if (depth > 0.2f) {
            int blur = Math.max(2, Math.round(depth * 1.6f));
            float offset = depth * 0.75f;
            softShadow(g, x + offset, y + offset, w, h, arc, blur, dark, AppTheme.isDarkMode() ? 120 : 110);
            softShadow(g, x - offset, y - offset, w, h, arc, blur, light, AppTheme.isDarkMode() ? 110 : 200);
        }
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
    }

    private static void softShadow(Graphics2D g, float x, float y, int w, int h, int arc,
            int blur, Color color, int maxAlpha) {
        int step = Math.max(1, Math.round(maxAlpha / (float) (blur + 1)));
        for (int i = blur; i >= 0; i--) {
            float grow = i;
            int a = Math.round(step * (1f - i / (float) (blur + 1)) * 1.4f);
            g.setColor(AppTheme.alpha(color, Math.min(255, a)));
            g.fill(new RoundRectangle2D.Float(x - grow, y - grow, w + grow * 2, h + grow * 2,
                    arc + grow * 2, arc + grow * 2));
        }
    }

    /** Pressed / sunken surface. {@code depth} is the inner shadow size. */
    public static void inset(Graphics2D g, int x, int y, int w, int h, int arc, float depth,
            Color fill) {
        inset(g, x, y, w, h, arc, depth, fill, AppTheme.shadowLight(), AppTheme.shadowDark());
    }

    public static void inset(Graphics2D g, int x, int y, int w, int h, int arc, float depth,
            Color fill, Color light, Color dark) {
        if (w <= 0 || h <= 0) {
            return;
        }
        antialias(g);
        Shape shape = new RoundRectangle2D.Float(x, y, w, h, arc, arc);
        g.setColor(fill);
        g.fill(shape);
        if (depth <= 0.2f) {
            return;
        }
        Shape oldClip = g.getClip();
        g.clip(shape);
        int layers = Math.max(2, Math.round(depth));
        int darkAlpha = AppTheme.isDarkMode() ? 150 : 120;
        int lightAlpha = AppTheme.isDarkMode() ? 60 : 200;
        for (int k = 1; k <= layers; k++) {
            float d = k * depth / layers;
            Area darkEdge = new Area(shape);
            darkEdge.subtract(new Area(new RoundRectangle2D.Float(x + d, y + d, w, h, arc, arc)));
            g.setColor(AppTheme.alpha(dark, darkAlpha / layers));
            g.fill(darkEdge);
            Area lightEdge = new Area(shape);
            lightEdge.subtract(new Area(new RoundRectangle2D.Float(x - d, y - d, w, h, arc, arc)));
            g.setColor(AppTheme.alpha(light, lightAlpha / layers));
            g.fill(lightEdge);
        }
        g.setClip(oldClip);
    }
}
