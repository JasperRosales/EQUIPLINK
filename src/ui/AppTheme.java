package src.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppTheme {

    public static final Color BG = new Color(245, 247, 250);
    public static final Color NAVY = new Color(30, 55, 85);
    public static final Color BLUE = new Color(45, 105, 175);

    private static final Color SIDEBAR_NAVY = new Color(22, 32, 54);
    private static final Color PAGE = new Color(246, 248, 252);
    private static final Color DARK_PAGE = new Color(17, 24, 39);
    private static Font regularFont;
    private static Font semiboldFont;
    private static boolean darkMode;

    private AppTheme() {
    }

    public static Color background() {
        return darkMode ? DARK_PAGE : PAGE;
    }

    public static Color surface() {
        return darkMode ? new Color(31, 41, 58) : Color.WHITE;
    }

    public static Color elevatedSurface() {
        return darkMode ? new Color(38, 50, 70) : new Color(250, 251, 254);
    }

    public static Color foreground() {
        return darkMode ? new Color(239, 243, 250) : new Color(31, 41, 61);
    }

    public static Color muted() {
        return darkMode ? new Color(164, 176, 195) : new Color(117, 129, 151);
    }

    public static Color border() {
        return darkMode ? new Color(56, 70, 93) : new Color(231, 235, 243);
    }

    public static Color primary() {
        return BLUE;
    }

    public static Color sidebar() {
        return SIDEBAR_NAVY;
    }

    public static Color success() {
        return new Color(19, 153, 113);
    }

    public static Color warning() {
        return new Color(221, 145, 39);
    }

    public static Color danger() {
        return new Color(210, 71, 79);
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void setDarkMode(boolean enabled) {
        darkMode = enabled;
    }

    public static Font regular(float size) {
        loadFonts();
        return regularFont.deriveFont(size);
    }

    public static Font semibold(float size) {
        loadFonts();
        return semiboldFont.deriveFont(size);
    }

    private static void loadFonts() {
        if (regularFont != null) {
            return;
        }

        Font regular = loadFont("Poppins-Regular.ttf");
        Font semibold = loadFont("Poppins-SemiBold.ttf");
        if (regular == null) {
            regular = new Font("SansSerif", Font.PLAIN, 12);
        }
        if (semibold == null) {
            semibold = regular.deriveFont(Font.BOLD);
        }
        regularFont = regular;
        semiboldFont = semibold;
    }

    private static Font loadFont(String name) {
        try (InputStream resource = AppTheme.class.getResourceAsStream("/src/assets/" + name)) {
            if (resource != null) {
                return Font.createFont(Font.TRUETYPE_FONT, resource);
            }
        } catch (IOException | java.awt.FontFormatException exception) {
            System.err.println("Unable to load " + name + ": " + exception.getMessage());
        }

        Path fontPath = Path.of("src", "assets", name);
        if (Files.isRegularFile(fontPath)) {
            try (InputStream font = Files.newInputStream(fontPath)) {
                return Font.createFont(Font.TRUETYPE_FONT, font);
            } catch (IOException | java.awt.FontFormatException exception) {
                System.err.println("Unable to load " + fontPath + ": " + exception.getMessage());
            }
        }

        return null;
    }
}
