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

/**
 * Animated "E circuit" logo drawn with Java2D vector shapes.
 *
 * Usage:
 *   LogoAnimationPanel logo = new LogoAnimationPanel();
 *   frame.add(logo);
 *   logo.start();
 *
 * Sprite support (for later):
 *   logo.setSprite(SpriteSheet.load(new File("logo_sheet.png"), 384, 256, 30));
 *   // the panel then plays your sprite sheet instead of the vector drawing.
 *   logo.setSprite(null); // back to the vector animation
 *
 * You can also bake this animation into a sprite sheet PNG:
 *   LogoAnimationPanel.exportSpriteSheet(new File("logo_sheet.png"), 384, 256, 30, 8);
 */
public class LogoAnimationPanel extends LogoAnimationPanelPart1 {

    public LogoAnimationPanel() {
        setPreferredSize(new Dimension(768, 512));
        setDoubleBuffered(true);
        setOpaque(false);

        topBand = buildTopBand();
        bar = buildBar();
        tracePts = new Point2D[]{
                nodeLeft,
                new Point2D.Double(825, 527),
                new Point2D.Double(965, 434),
                nodeRight
        };
        trace = new Path2D.Double();
        trace.moveTo(tracePts[0].getX(), tracePts[0].getY());
        for (int i = 1; i < tracePts.length; i++) trace.lineTo(tracePts[i].getX(), tracePts[i].getY());
        bottomBand = buildBottomBand();

        timer = new Timer(1000 / 60, e -> {
            repaint();
            if (isFinished()) {
                ((Timer) e.getSource()).stop();
                Runnable done = onFinished;
                onFinished = null;
                if (done != null) done.run();
            }
        });
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        if (background != null) {
            g2.setColor(background);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        if (sprite != null) {
            sprite.draw(g2, time(), getWidth(), getHeight());
        } else {
            renderFrame(g2, time(), getWidth(), getHeight());
        }
        g2.dispose();
    }

    /** Draws the logo at time t (seconds) fitted inside w x h. Reusable for exporting. */
    public void renderFrame(Graphics2D g, double t, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // fit the logo bounds into the panel, centered, with a little margin
        double margin = 0.08;
        double s = Math.min(w * (1 - 2 * margin) / LOGO_BOUNDS.getWidth(),
                            h * (1 - 2 * margin) / LOGO_BOUNDS.getHeight());
        AffineTransform old = g.getTransform();
        g.translate(w / 2.0, h / 2.0);
        g.scale(s, s);
        g.translate(-LOGO_BOUNDS.getCenterX(), -LOGO_BOUNDS.getCenterY());
        drawLogo(g, t);
        g.setTransform(old);
    }

    /** Draws the logo at time t in design coordinates (caller sets the transform). */
    void drawLogo(Graphics2D g, double t) {
        // 1) the three red bands sweep in (staggered)
        drawSweep(g, topBand,    progress(t, 0.00, 0.80), -1);
        drawSweep(g, bottomBand, progress(t, 0.30, 1.10), +1);
        drawSweep(g, bar,        progress(t, 0.70, 1.30), +1);

        // 2) circuit trace draws itself
        double traceP = progress(t, 1.10, 2.00);
        double nodeP  = progress(t, 1.90, 2.35);
        if (traceP > 0) drawTrace(g, traceP, nodeP, t);

        // 3) light shine passes over the logo
        double shineP = progress(t, 2.30, 3.10);
        if (shineP > 0 && shineP < 1) drawShine(g, shineP);
    }

    /**
     * Renders the finished logo as vector art straight at the requested pixel size, with
     * the mark filling the image. Use this for still logos so they stay sharp at any
     * size or display scale (the sprite sheet frames are only 384x256).
     */
    public static BufferedImage renderStill(int width, int height) {
        LogoAnimationPanel renderer = new LogoAnimationPanel();
        Rectangle2D b = renderer.markBounds();
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        double s = Math.min(width / b.getWidth(), height / b.getHeight());
        g.translate(width / 2.0, height / 2.0);
        g.scale(s, s);
        g.translate(-b.getCenterX(), -b.getCenterY());
        renderer.drawLogo(g, INTRO_LENGTH);
        g.dispose();
        return image;
    }

    /** Width / height of the finished mark. */
    public static double markAspect() {
        Rectangle2D b = new LogoAnimationPanel().markBounds();
        return b.getWidth() / b.getHeight();
    }

    private Rectangle2D markBounds() {
        Rectangle2D b = topBand.getBounds2D();
        b.add(bottomBand.getBounds2D());
        b.add(bar.getBounds2D());
        // Small breathing room for anti-aliased edges and the node rings.
        double pad = 6;
        return new Rectangle2D.Double(b.getX() - pad, b.getY() - pad,
                b.getWidth() + pad * 2, b.getHeight() + pad * 2);
    }

    // ------------------------------------------------------------------ sprite export

    /**
     * Bakes the intro animation into a sprite sheet PNG (transparent background).
     * @param out      output file
     * @param frameW   width of one frame
     * @param frameH   height of one frame
     * @param fps      frames per second
     * @param columns  frames per row in the sheet
     */
    public static void exportSpriteSheet(File out, int frameW, int frameH, int fps, int columns) throws IOException {
        LogoAnimationPanel renderer = new LogoAnimationPanel();
        int frames = (int) Math.ceil(INTRO_LENGTH * fps) + 1;
        int rows = (int) Math.ceil(frames / (double) columns);
        BufferedImage sheet = new BufferedImage(frameW * columns, frameH * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < frames; i++) {
            int x = (i % columns) * frameW, y = (i / columns) * frameH;
            Graphics2D fg = (Graphics2D) g.create(x, y, frameW, frameH);
            renderer.renderFrame(fg, i / (double) fps, frameW, frameH);
            fg.dispose();
        }
        g.dispose();
        ImageIO.write(sheet, "png", out);
        System.out.println("Wrote " + frames + " frames (" + columns + "x" + rows + ") to " + out);
    }

    /** The bundled EquipLink logo sheet, or null if it is missing. */
    public static synchronized BufferedImage bundledSheet() {
        if (sheetLoaded) return sheetImage;
        sheetLoaded = true;
        try (InputStream in = LogoAnimationPanel.class.getResourceAsStream("/src/assets/logo_sheet.png")) {
            if (in != null) sheetImage = ImageIO.read(in);
        } catch (IOException e) {
            System.err.println("Unable to load logo sheet: " + e.getMessage());
        }
        Path path = Path.of("src", "assets", "logo_sheet.png");
        if (sheetImage == null && Files.isRegularFile(path)) {
            try {
                sheetImage = ImageIO.read(path.toFile());
            } catch (IOException e) {
                System.err.println("Unable to load logo sheet: " + e.getMessage());
            }
        }
        return sheetImage;
    }

    /** The bundled logo sheet as a sprite, or null if it is missing. */
    public static SpriteSheet bundledSprite(boolean loop) {
        BufferedImage sheet = bundledSheet();
        return sheet == null ? null
                : SpriteSheet.from(sheet, SHEET_FRAME_W, SHEET_FRAME_H, SHEET_FPS, 0, loop);
    }

    /** A logo that plays the bundled sprite once and holds the final frame. */
    public static LogoAnimationPanel intro(int width, int height) {
        LogoAnimationPanel panel = new LogoAnimationPanel();
        panel.setLooping(false);
        Dimension size = new Dimension(width, height);
        panel.setPreferredSize(size);
        panel.setMinimumSize(size);
        panel.setMaximumSize(size);
        SpriteSheet sprite = bundledSprite(false);
        if (sprite != null) panel.sprite = sprite;
        panel.start();
        return panel;
    }
}
