package src.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the EquipLink logo (sprite mark and full artwork), trims padding, and scales it. */
public final class BrandLogo {

    private static final String FILE = "received_1676750560533488-removebg-preview.png";
    private static BufferedImage full;
    private static BufferedImage icon;
    private static boolean loaded;

    private BrandLogo() {
    }

    /** Full logo (mark and wordmark) at the given height, or null if missing. */
    public static ImageIcon full(int height) {
        load();
        return scale(full, height);
    }

    /** Display scales to pre-render, so HiDPI screens get real pixels, not upscaling. */
    private static final double[] SCALES = { 1, 1.25, 1.5, 1.75, 2, 2.5, 3 };

    /** Sharp logo mark at the given height (logical px), rendered from vector art. */
    public static ImageIcon icon(int height) {
        int width = Math.max(1, (int) Math.round(height * src.ui.components.LogoAnimationPanel.markAspect()));
        return new ImageIcon(multiResolution(width, height,
                (w, h) -> src.ui.components.LogoAnimationPanel.renderStill(w, h)));
    }

    /** The logo mark centred on a rounded square of the given color. */
    public static ImageIcon badge(int size, java.awt.Color background) {
        return new ImageIcon(multiResolution(size, size, (w, h) -> renderBadge(w, background)));
    }

    /** Window icons at the sizes Windows and Linux pick from. */
    public static java.util.List<java.awt.Image> windowIcons() {
        java.util.List<java.awt.Image> icons = new java.util.ArrayList<>();
        for (int size : new int[] { 16, 20, 24, 32, 40, 48, 64, 128, 256 }) {
            icons.add(renderBadge(size, src.ui.AppTheme.LIGHT_BG));
        }
        return icons;
    }

    private static BufferedImage renderBadge(int size, java.awt.Color background) {
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(background);
        g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, size, size, size / 3.0, size / 3.0));
        double pad = Math.max(2, size / 9.0);
        double aspect = src.ui.components.LogoAnimationPanel.markAspect();
        double boxW = size - pad * 2;
        double boxH = size - pad * 2;
        int w = (int) Math.round(Math.min(boxW, boxH * aspect));
        int h = (int) Math.round(w / aspect);
        g.drawImage(src.ui.components.LogoAnimationPanel.renderStill(w, h),
                (size - w) / 2, (size - h) / 2, null);
        g.dispose();
        return result;
    }

    private interface Renderer {
        BufferedImage render(int width, int height);
    }

    private static java.awt.Image multiResolution(int width, int height, Renderer renderer) {
        java.awt.Image[] variants = new java.awt.Image[SCALES.length];
        for (int i = 0; i < SCALES.length; i++) {
            variants[i] = renderer.render(
                    Math.max(1, (int) Math.round(width * SCALES[i])),
                    Math.max(1, (int) Math.round(height * SCALES[i])));
        }
        return new java.awt.image.BaseMultiResolutionImage(variants);
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        BufferedImage source = read();
        full = source == null ? null : trim(source, 0, source.getHeight());
        // The mark sits above the wordmark; split at the tallest transparent row gap.
        int gapStart = -1;
        int bestStart = -1;
        int bestLength = 0;
        for (int y = 0; full != null && y < full.getHeight(); y++) {
            if (rowEmpty(full, y)) {
                if (gapStart < 0) {
                    gapStart = y;
                }
                if (y - gapStart + 1 > bestLength) {
                    bestLength = y - gapStart + 1;
                    bestStart = gapStart;
                }
            } else {
                gapStart = -1;
            }
        }
        icon = bestStart > 0 ? trim(full, 0, bestStart) : full;
    }

    private static BufferedImage read() {
        try (InputStream resource = BrandLogo.class.getResourceAsStream("/src/assets/" + FILE)) {
            if (resource != null) {
                return ImageIO.read(resource);
            }
        } catch (IOException exception) {
            System.err.println("Unable to load logo: " + exception.getMessage());
        }
        Path path = Path.of("src", "assets", FILE);
        if (Files.isRegularFile(path)) {
            try {
                return ImageIO.read(path.toFile());
            } catch (IOException exception) {
                System.err.println("Unable to load logo: " + exception.getMessage());
            }
        }
        return null;
    }

    private static boolean rowEmpty(BufferedImage image, int y) {
        for (int x = 0; x < image.getWidth(); x++) {
            if (opaque(image, x, y)) {
                return false;
            }
        }
        return true;
    }

    private static boolean opaque(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) > 24;
    }

    /** Crops rows [top, bottom) to the bounds of their visible pixels. */
    private static BufferedImage trim(BufferedImage image, int top, int bottom) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = top; y < bottom; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (opaque(image, x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static ImageIcon scale(BufferedImage image, int height) {
        if (image == null) {
            return null;
        }
        int width = Math.max(1, Math.round(image.getWidth() * (height / (float) image.getHeight())));
        BufferedImage current = image;
        // Halve repeatedly for smooth downscaling, then finish at the exact size.
        while (current.getHeight() / 2 >= height) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return new ImageIcon(resize(current, width, height));
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return result;
    }
}
