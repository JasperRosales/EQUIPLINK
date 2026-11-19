package src.ui.components;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Part 1 of LogoAnimationPanel, split out to keep files short. */
public abstract class LogoAnimationPanelPart1 extends JPanel {

    // ---- design space (matches the original 1536x1024 artwork) ----
    static final double DESIGN_W = 1536, DESIGN_H = 1024;
    static final Rectangle2D LOGO_BOUNDS = new Rectangle2D.Double(230, 85, 1080, 820);

    // ---- colors ----
    static final Color RED_BRIGHT = new Color(0xD45060);
    static final Color RED_MID    = new Color(0x800020);
    static final Color RED_DARK   = new Color(0x3D0010);

    // ---- timeline (seconds) ----
    public static final double INTRO_LENGTH = 3.2;   // length of the intro
    public static final double LOOP_LENGTH  = 5.0;   // intro + idle before looping

    Shape topBand, bottomBand, bar;
    Path2D trace;
    Point2D[] tracePts;
    final Point2D nodeLeft = new Point2D.Double(632, 527);
    final Point2D nodeRight = new Point2D.Double(1160, 432);

    Timer timer;
    long startNanos;
    boolean looping = true;
    Color background = null;
    SpriteSheet sprite;   // optional, set later
    Runnable onFinished;

    // ------------------------------------------------------------------ public API

    public void start() { startNanos = System.nanoTime(); timer.start(); }
    public void stop() { timer.stop(); }
    public void restart() { startNanos = System.nanoTime(); if (!timer.isRunning()) timer.start(); }
    public void setLooping(boolean looping) { this.looping = looping; }
    public void setLogoBackground(Color c) { this.background = c; setOpaque(c != null); repaint(); } // null = transparent

    /** Runs once when a non-looping animation reaches its last frame. */
    public void setOnFinished(Runnable onFinished) { this.onFinished = onFinished; }

    /** True once a non-looping animation has played through. */
    public boolean isFinished() {
        if (sprite != null) return sprite.finished(time());
        return !looping && time() >= INTRO_LENGTH + 0.4;
    }

    /** Play a sprite sheet instead of the vector logo. Pass null to go back. */
    public void setSprite(SpriteSheet sprite) { this.sprite = sprite; restart(); }

    /** Current animation time in seconds. */
    public double time() {
        double t = (System.nanoTime() - startNanos) / 1e9;
        if (sprite != null) return t;
        return looping ? t % LOOP_LENGTH : Math.min(t, LOOP_LENGTH);
    }

    // ------------------------------------------------------------------ animation pieces

    void drawSweep(Graphics2D g, Shape shape, double p, int dir) {
        if (p <= 0) return;
        double e = easeOutCubic(p);
        Rectangle2D b = shape.getBounds2D();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, p * 1.6)));
        g2.translate(dir * (1 - e) * 120, 0);                // slide in
        // reveal from left to right with a slanted edge (feels like a swoosh)
        double revealX = b.getMinX() - 200 + (b.getWidth() + 400) * e;
        Path2D clip = new Path2D.Double();
        clip.moveTo(b.getMinX() - 400, b.getMinY() - 50);
        clip.lineTo(revealX + 120, b.getMinY() - 50);
        clip.lineTo(revealX - 120, b.getMaxY() + 50);
        clip.lineTo(b.getMinX() - 400, b.getMaxY() + 50);
        clip.closePath();
        g2.clip(clip);
        fillShaded(g2, shape);
        g2.dispose();
    }

    void fillShaded(Graphics2D g, Shape shape) {
        Rectangle2D b = shape.getBounds2D();
        // main diagonal gradient: bright top-left -> dark bottom-right
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(b.getMinX(), b.getMinY()),
                new Point2D.Double(b.getMaxX(), b.getMaxY()),
                new float[]{0f, 0.45f, 1f},
                new Color[]{RED_BRIGHT, RED_MID, RED_DARK}));
        g.fill(shape);
        // inner shadow from the bottom for a folded-ribbon look
        g.setPaint(new GradientPaint(
                (float) b.getCenterX(), (float) b.getMinY(), new Color(0, 0, 0, 0),
                (float) b.getCenterX(), (float) b.getMaxY(), new Color(30, 0, 8, 110)));
        g.fill(shape);
    }

    void drawTrace(Graphics2D g, double p, double nodeP, double t) {
        double e = easeInOutCubic(p);
        Path2D partial = partialPolyline(tracePts, e);
        Point2D tip = pointAlong(tracePts, e);

        Graphics2D g2 = (Graphics2D) g.create();
        // white groove
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(32, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(partial);
        // dark copper line inside groove
        g2.setPaint(new GradientPaint(620, 0, new Color(0x800020), 1160, 0, new Color(0x5C0017)));
        g2.setStroke(new BasicStroke(18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(partial);

        // glowing tip while it is still drawing
        if (p < 1) {
            for (int i = 4; i >= 1; i--) {
                g2.setColor(new Color(0xD4, 0x50, 0x60, 50));
                double r = 10 + i * 8;
                g2.fill(new Ellipse2D.Double(tip.getX() - r, tip.getY() - r, r * 2, r * 2));
            }
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(tip.getX() - 8, tip.getY() - 8, 16, 16));
        }

        // nodes pop in with a slight overshoot, then gently pulse while idle
        drawNode(g2, nodeLeft, Math.min(1, e * 8), t);
        if (nodeP > 0) drawNode(g2, nodeRight, easeOutBack(nodeP), t);
        g2.dispose();
    }

    void drawNode(Graphics2D g, Point2D c, double scale, double t) {
        if (scale <= 0) return;
        double pulse = (t > INTRO_LENGTH) ? 1 + 0.06 * Math.sin((t - INTRO_LENGTH) * 4) : 1;
        double ring = 40 * scale * pulse, core = 26 * scale * pulse;
        if (t > INTRO_LENGTH) {  // soft glow while idle
            float a = (float) (0.25 + 0.2 * Math.sin((t - INTRO_LENGTH) * 4));
            g.setColor(new Color(0.83f, 0.31f, 0.38f, Math.max(0, a)));
            double gr = ring + 14;
            g.fill(new Ellipse2D.Double(c.getX() - gr, c.getY() - gr, gr * 2, gr * 2));
        }
        g.setColor(new Color(0x5C0017));
        g.fill(new Ellipse2D.Double(c.getX() - ring, c.getY() - ring, ring * 2, ring * 2));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(c.getX() - core, c.getY() - core, core * 2, core * 2));
    }

    void drawShine(Graphics2D g, double p) {
        Area logo = new Area(topBand);
        logo.add(new Area(bottomBand));
        logo.add(new Area(bar));
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clip(logo);
        double x = LOGO_BOUNDS.getMinX() - 300 + (LOGO_BOUNDS.getWidth() + 600) * easeInOutCubic(p);
        g2.setPaint(new LinearGradientPaint(
                new Point2D.Double(x - 120, 0), new Point2D.Double(x + 120, 0),
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(255, 255, 255, 0), new Color(255, 255, 255, 140), new Color(255, 255, 255, 0)}));
        AffineTransform skew = AffineTransform.getShearInstance(-0.35, 0);
        g2.transform(skew);
        g2.fill(new Rectangle2D.Double(x - 120 - 400, 0, 240 + 800, DESIGN_H));
        g2.dispose();
    }

    // ------------------------------------------------------------------ shapes (design coords)

    static Shape buildTopBand() {
        Path2D p = new Path2D.Double();
        p.moveTo(318, 537);
        p.curveTo(440, 290, 560, 98, 860, 98);           // outer edge up to the top
        p.lineTo(1328, 98);
        p.curveTo(1250, 210, 1170, 312, 1050, 312);      // right tip curling down
        p.lineTo(752, 312);
        p.curveTo(690, 312, 655, 345, 612, 416);         // inner curve
        p.curveTo(500, 425, 400, 460, 318, 537);         // back to the tip
        p.closePath();
        return p;
    }

    static Shape buildBottomBand() {
        Path2D p = new Path2D.Double();
        p.moveTo(612, 438);
        p.lineTo(828, 438);
        p.lineTo(935, 375);                              // circuit step up
        p.curveTo(950, 368, 960, 367, 975, 367);
        p.lineTo(1165, 367);
        p.curveTo(1210, 367, 1242, 400, 1242, 435);      // rounded right end
        p.curveTo(1242, 472, 1210, 503, 1165, 503);
        p.lineTo(992, 503);
        p.curveTo(975, 503, 965, 507, 955, 515);
        p.lineTo(862, 588);                              // circuit step down
        p.curveTo(850, 598, 838, 603, 820, 603);
        p.lineTo(552, 603);
        p.curveTo(525, 635, 512, 660, 516, 685);         // inner C curve
        p.curveTo(522, 715, 548, 732, 582, 732);
        p.lineTo(1225, 732);
        p.curveTo(1130, 820, 1040, 888, 940, 888);       // bottom-right swoop
        p.lineTo(395, 888);
        p.curveTo(300, 880, 245, 820, 245, 740);         // outer left of the C
        p.curveTo(250, 570, 420, 450, 612, 438);
        p.closePath();
        return p;
    }

    static Shape buildBar() {
        Path2D p = new Path2D.Double();
        p.moveTo(702, 698);
        p.lineTo(755, 658);
        p.curveTo(760, 655, 765, 654, 772, 654);
        p.lineTo(1291, 654);
        p.lineTo(1244, 698);
        p.closePath();
        return p;
    }

    // ------------------------------------------------------------------ helpers

    static double progress(double t, double start, double end) {
        return Math.max(0, Math.min(1, (t - start) / (end - start)));
    }
    static double easeOutCubic(double x) { return 1 - Math.pow(1 - x, 3); }
    static double easeInOutCubic(double x) {
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }
    static double easeOutBack(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    static double totalLength(Point2D[] pts) {
        double len = 0;
        for (int i = 1; i < pts.length; i++) len += pts[i - 1].distance(pts[i]);
        return len;
    }
    static Point2D pointAlong(Point2D[] pts, double frac) {
        double target = totalLength(pts) * frac;
        for (int i = 1; i < pts.length; i++) {
            double seg = pts[i - 1].distance(pts[i]);
            if (target <= seg) {
                double k = seg == 0 ? 0 : target / seg;
                return new Point2D.Double(
                        pts[i - 1].getX() + (pts[i].getX() - pts[i - 1].getX()) * k,
                        pts[i - 1].getY() + (pts[i].getY() - pts[i - 1].getY()) * k);
            }
            target -= seg;
        }
        return pts[pts.length - 1];
    }
    static Path2D partialPolyline(Point2D[] pts, double frac) {
        Path2D path = new Path2D.Double();
        path.moveTo(pts[0].getX(), pts[0].getY());
        double target = totalLength(pts) * frac;
        for (int i = 1; i < pts.length; i++) {
            double seg = pts[i - 1].distance(pts[i]);
            if (target >= seg) {
                path.lineTo(pts[i].getX(), pts[i].getY());
                target -= seg;
            } else {
                Point2D end = pointAlong(new Point2D[]{pts[i - 1], pts[i]}, target / seg);
                path.lineTo(end.getX(), end.getY());
                break;
            }
        }
        return path;
    }

    // ------------------------------------------------------------------ bundled sprite

    /** Frame layout of src/assets/logo_sheet.png (see exportSpriteSheet). */
    public static final int SHEET_FRAME_W = 384, SHEET_FRAME_H = 256, SHEET_FPS = 30;
    static BufferedImage sheetImage;
    static boolean sheetLoaded;

    // ------------------------------------------------------------------ sprite playback

    /** A simple grid sprite sheet: frames left-to-right, top-to-bottom. */
    public static class SpriteSheet {
        final BufferedImage[] frames;
        final int fps;
        final boolean loop;

        public SpriteSheet(BufferedImage[] frames, int fps, boolean loop) {
            this.frames = frames; this.fps = fps; this.loop = loop;
        }

        /** Load a grid sheet. frameCount <= 0 means "use every cell". */
        public static SpriteSheet load(File file, int frameW, int frameH, int fps, int frameCount, boolean loop)
                throws IOException {
            return from(ImageIO.read(file), frameW, frameH, fps, frameCount, loop);
        }

        /** Splits an already-loaded grid sheet into frames. */
        public static SpriteSheet from(BufferedImage sheet, int frameW, int frameH, int fps, int frameCount,
                boolean loop) {
            int cols = sheet.getWidth() / frameW, rows = sheet.getHeight() / frameH;
            int n = frameCount > 0 ? Math.min(frameCount, cols * rows) : cols * rows;
            while (frameCount <= 0 && n > 1 && isBlank(sheet, ((n - 1) % cols) * frameW,
                    ((n - 1) / cols) * frameH, frameW, frameH)) n--;
            BufferedImage[] f = new BufferedImage[n];
            for (int i = 0; i < n; i++)
                f[i] = sheet.getSubimage((i % cols) * frameW, (i / cols) * frameH, frameW, frameH);
            return new SpriteSheet(f, fps, loop);
        }

        public static SpriteSheet load(File file, int frameW, int frameH, int fps) throws IOException {
            return load(file, frameW, frameH, fps, 0, false);
        }

        static boolean isBlank(BufferedImage img, int x0, int y0, int w, int h) {
            for (int y = y0; y < y0 + h; y += 4)
                for (int x = x0; x < x0 + w; x += 4)
                    if ((img.getRGB(x, y) >>> 24) != 0) return false;
            return true;
        }

        public int frameCount() { return frames.length; }

        /** The final (resting) frame of the animation. */
        public BufferedImage lastFrame() { return frames[frames.length - 1]; }

        boolean finished(double t) { return !loop && t * fps >= frames.length; }

        void draw(Graphics2D g, double t, int w, int h) {
            int i = (int) (t * fps);
            i = loop ? i % frames.length : Math.min(i, frames.length - 1);
            BufferedImage img = frames[i];
            double s = Math.min(w / (double) img.getWidth(), h / (double) img.getHeight());
            int dw = (int) (img.getWidth() * s), dh = (int) (img.getHeight() * s);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        }
    }
}
