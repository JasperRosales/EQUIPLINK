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

/** Loads the EquipLink logo, trims its transparent padding, and scales it. */
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

    /** Logo mark only (no wordmark) at the given height, or null if missing. */
    public static ImageIcon icon(int height) {
        load();
        return scale(icon, height);
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        BufferedImage source = read();
        if (source == null) {
            return;
        }
        full = trim(source, 0, source.getHeight());
        if (full == null) {
            return;
        }
        // The mark sits above the wordmark; split at the tallest transparent row gap.
        int gapStart = -1;
        int bestStart = -1;
        int bestLength = 0;
        for (int y = 0; y < full.getHeight(); y++) {
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
