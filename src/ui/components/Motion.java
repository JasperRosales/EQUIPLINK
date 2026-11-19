package src.ui.components;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.Timer;
import javax.swing.plaf.LayerUI;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;

/** Shared easing, one-shot animation clocks, and entrance effects. */
public final class Motion {

    private static final int FRAME_MS = 15;

    private Motion() {
    }

    public static float clamp(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    public static float easeOut(float t) {
        t = clamp(t);
        return 1f - (float) Math.pow(1f - t, 3);
    }

    /** Ease-out with a small overshoot, for things that "pop". */
    public static float easeOutBack(float t) {
        t = clamp(t);
        float c1 = 1.4f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    /** A restartable one-shot animation that repaints its owner while it runs. */
    public static final class Clock {
        private final Component owner;
        private final int durationMs;
        private final Timer timer;
        private long startedAt = -1;

        public Clock(Component owner, int durationMs) {
            this.owner = owner;
            this.durationMs = durationMs;
            this.timer = new Timer(FRAME_MS, e -> {
                owner.repaint();
                if (!running() || !owner.isDisplayable()) {
                    ((Timer) e.getSource()).stop();
                }
            });
            this.timer.setCoalesce(true);
        }

        public void start() {
            startedAt = System.currentTimeMillis();
            timer.restart();
        }

        public boolean running() {
            return startedAt >= 0 && progress() < 1f;
        }

        /** 0..1 since start, or 1 when idle. */
        public float progress() {
            if (startedAt < 0) {
                return 1f;
            }
            return clamp((System.currentTimeMillis() - startedAt) / (float) durationMs);
        }
    }

    /** Diagonal light sweep across {@code shape}; {@code t} runs 0..1. */
    public static void paintShine(Graphics2D g, Shape shape, float t, Color tint, int alpha) {
        if (t <= 0f || t >= 1f) {
            return;
        }
        java.awt.Rectangle b = shape.getBounds();
        float x = b.x - b.height - b.width * 0.35f + (b.width * 1.7f + b.height) * easeOut(t);
        float band = Math.max(28f, b.width * 0.22f);
        Graphics2D s = (Graphics2D) g.create();
        s.clip(shape);
        Color clear = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 0);
        Color peak = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), alpha);
        s.setPaint(new LinearGradientPaint(
                new Point2D.Float(x, b.y), new Point2D.Float(x + band, b.y + b.height * 0.6f),
                new float[] { 0f, 0.5f, 1f }, new Color[] { clear, peak, clear }));
        s.fill(b);
        s.dispose();
    }

    /** Expanding, fading circle from a press point, clipped to {@code shape}. */
    public static void paintRipple(Graphics2D g, Shape shape, Point2D origin, float t, Color tint,
            int alpha) {
        if (origin == null || t <= 0f || t >= 1f) {
            return;
        }
        java.awt.Rectangle b = shape.getBounds();
        double reach = Math.hypot(Math.max(origin.getX() - b.x, b.getMaxX() - origin.getX()),
                Math.max(origin.getY() - b.y, b.getMaxY() - origin.getY()));
        double r = reach * easeOut(t);
        Graphics2D s = (Graphics2D) g.create();
        s.clip(shape);
        s.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(),
                Math.round(alpha * (1f - t))));
        s.fill(new Ellipse2D.Double(origin.getX() - r, origin.getY() - r, r * 2, r * 2));
        s.dispose();
    }

    /**
     * Wraps content so it pops in (fade, slight rise and scale with overshoot) the first
     * time it is painted. Use for dialogs, cards and other surfaces that appear.
     */
    public static <T extends JComponent> JLayer<T> popIn(T content) {
        return popIn(content, 0);
    }

    public static <T extends JComponent> JLayer<T> popIn(T content, int delayMs) {
        return new JLayer<>(content, new PopInUI<>(delayMs));
    }

    private static final class PopInUI<T extends JComponent> extends LayerUI<T> {
        private static final int DURATION = 320;
        private final int delayMs;
        private long startedAt = -1;
        private Timer timer;

        PopInUI(int delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public void paint(Graphics graphics, JComponent c) {
            if (startedAt < 0) {
                startedAt = System.currentTimeMillis() + delayMs;
                timer = new Timer(FRAME_MS, e -> {
                    c.repaint();
                    if (System.currentTimeMillis() - startedAt > DURATION || !c.isDisplayable()) {
                        ((Timer) e.getSource()).stop();
                    }
                });
                timer.setCoalesce(true);
                timer.start();
            }
            float t = clamp((System.currentTimeMillis() - startedAt) / (float) DURATION);
            if (t >= 1f) {
                super.paint(graphics, c);
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            if (c.getParent() instanceof javax.swing.JLayeredPane) {
                // As a window's content pane, clear to the window color while fading in.
                Component view = ((JLayer<?>) c).getView();
                g.setColor(view != null && view.isOpaque() ? view.getBackground()
                        : javax.swing.UIManager.getColor("Panel.background"));
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
            }
            if (t <= 0f) {
                g.dispose();
                return;
            }
            float scale = 0.93f + 0.07f * easeOutBack(t);
            double cx = c.getWidth() / 2.0;
            double cy = c.getHeight() / 2.0;
            g.translate(0, (1f - easeOut(t)) * 14);
            g.translate(cx, cy);
            g.scale(scale, scale);
            g.translate(-cx, -cy);
            g.setComposite(AlphaComposite.SrcOver.derive(easeOut(Math.min(1f, t * 1.6f))));
            super.paint(g, c);
            g.dispose();
        }
    }
}
