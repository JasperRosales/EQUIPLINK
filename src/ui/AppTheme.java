package src.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppTheme {

    // ---- EquipLink palette (single source of truth for every screen) ----
    // colorhunt.co/palette/053b50176b8764ccc5eeeeee
    /** Brand primary: deep ocean teal. */
    public static final Color PRIMARY = new Color(0x17, 0x6B, 0x87);
    /** Soft aqua highlight. */
    public static final Color PRIMARY_LIGHT = new Color(0x64, 0xCC, 0xC5);
    /** Darkest teal, used for depth and the sidebar. */
    public static final Color PRIMARY_DARK = new Color(0x05, 0x3B, 0x50);
    /** Light-mode ink and neutrals (also used by light-only dialogs). */
    public static final Color INK = new Color(0x05, 0x3B, 0x50);
    public static final Color INK_MUTED = new Color(92, 124, 134);
    public static final Color LIGHT_BG = new Color(0xEE, 0xEE, 0xEE);
    public static final Color LIGHT_BG_DEEP = new Color(0xE2, 0xE8, 0xE9);
    public static final Color LIGHT_BORDER = new Color(0xD2, 0xDB, 0xDD);

    // Legacy aliases kept for compatibility.
    public static final Color BG = LIGHT_BG;
    public static final Color NAVY = INK;
    public static final Color BLUE = PRIMARY;

    private static final Color DARK_BG = new Color(0x07, 0x22, 0x2E);
    private static final Color SIDEBAR = PRIMARY_DARK;
    private static Font regularFont;
    private static Font semiboldFont;
    private static boolean darkMode;

    private AppTheme() {
    }

    public static Color background() {
        return darkMode ? DARK_BG : LIGHT_BG;
    }

    /** Slightly deeper page tone, used for gradients and sunken areas. */
    public static Color backgroundDeep() {
        return darkMode ? new Color(0x05, 0x1B, 0x25) : LIGHT_BG_DEEP;
    }

    /** Neumorphic surfaces share the page color; depth comes from shadows. */
    public static Color surface() {
        return background();
    }

    /** Input / inset fill. */
    public static Color elevatedSurface() {
        return darkMode ? new Color(0x10, 0x32, 0x3E) : new Color(0xF6, 0xF8, 0xF8);
    }

    public static Color foreground() {
        return darkMode ? new Color(0xEE, 0xEE, 0xEE) : INK;
    }

    public static Color muted() {
        return darkMode ? new Color(0x9C, 0xBB, 0xC2) : INK_MUTED;
    }

    public static Color border() {
        return darkMode ? new Color(0x1A, 0x41, 0x50) : LIGHT_BORDER;
    }

    /** Top-left highlight of a neumorphic surface. */
    public static Color shadowLight() {
        return darkMode ? new Color(0x1C, 0x46, 0x55) : Color.WHITE;
    }

    /** Bottom-right shade of a neumorphic surface. */
    public static Color shadowDark() {
        return darkMode ? new Color(0x04, 0x18, 0x21) : new Color(0xBE, 0xC7, 0xC9);
    }

    public static Color primary() {
        return PRIMARY;
    }

    public static Color primaryDark() {
        return PRIMARY_DARK;
    }

    public static Color sidebar() {
        return SIDEBAR;
    }

    public static Color sidebarText() {
        return new Color(0xEE, 0xEE, 0xEE);
    }

    public static Color sidebarMuted() {
        return new Color(0x9C, 0xC2, 0xC6);
    }

    public static Color sidebarDivider() {
        return new Color(0x1D, 0x4F, 0x63);
    }

    public static Color sidebarShadowLight() {
        return new Color(0x23, 0x59, 0x6C);
    }

    public static Color sidebarShadowDark() {
        return new Color(0x03, 0x24, 0x32);
    }

    public static Color success() {
        return new Color(28, 168, 120);
    }

    public static Color warning() {
        return new Color(232, 145, 40);
    }

    public static Color danger() {
        return new Color(228, 76, 88);
    }

    /** Secondary accent used in charts and stat cards. */
    public static Color accent() {
        return PRIMARY_LIGHT;
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    public static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void setDarkMode(boolean enabled) {
        darkMode = enabled;
        install();
        com.formdev.flatlaf.FlatLaf.updateUI();
    }

    /** Installs the FlatLaf look and feel with EquipLink's colors and fonts. */
    public static void install() {
        javax.swing.UIManager.put("defaultFont", regular(13));
        javax.swing.UIManager.put("@accentColor", hex(PRIMARY));
        javax.swing.UIManager.put("Component.arc", 14);
        javax.swing.UIManager.put("Button.arc", 14);
        javax.swing.UIManager.put("TextComponent.arc", 14);
        javax.swing.UIManager.put("CheckBox.arc", 6);
        javax.swing.UIManager.put("Component.focusWidth", 1);
        javax.swing.UIManager.put("Component.innerFocusWidth", 0);
        javax.swing.UIManager.put("Component.focusColor", alpha(PRIMARY, 90));
        javax.swing.UIManager.put("Component.focusedBorderColor", PRIMARY);
        java.awt.Insets fieldMargin = new java.awt.Insets(8, 10, 8, 10);
        javax.swing.UIManager.put("TextField.margin", fieldMargin);
        javax.swing.UIManager.put("PasswordField.margin", fieldMargin);
        javax.swing.UIManager.put("FormattedTextField.margin", fieldMargin);
        javax.swing.UIManager.put("TextArea.margin", fieldMargin);
        javax.swing.UIManager.put("ComboBox.padding", new java.awt.Insets(6, 8, 6, 8));
        javax.swing.UIManager.put("Spinner.padding", new java.awt.Insets(6, 8, 6, 8));
        javax.swing.UIManager.put("Spinner.editorBorderPainted", false);
        javax.swing.UIManager.put("ScrollBar.width", 10);
        javax.swing.UIManager.put("ScrollBar.thumbArc", 999);
        javax.swing.UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        javax.swing.UIManager.put("ScrollBar.showButtons", false);
        javax.swing.UIManager.put("ScrollBar.track", new Color(0, 0, 0, 0));
        javax.swing.UIManager.put("OptionPane.buttonAreaBorder",
                javax.swing.BorderFactory.createEmptyBorder(12, 0, 0, 0));
        javax.swing.UIManager.put("Table.rowHeight", 34);
        javax.swing.UIManager.put("Table.showHorizontalLines", true);
        javax.swing.UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
        javax.swing.UIManager.put("TableHeader.height", 36);
        javax.swing.UIManager.put("TitlePane.unifiedBackground", true);
        installAlignmentFix();
        java.util.Map<String, String> extras = new java.util.HashMap<>();
        // Inputs read as soft wells; panels share the neumorphic page tone.
        String page = hex(background());
        String well = hex(elevatedSurface());
        String text = hex(foreground());
        for (String component : new String[] {
                "ComboBox", "TextField", "PasswordField", "FormattedTextField",
                "TextArea", "Spinner", "EditorPane", "TextPane" }) {
            extras.put(component + ".background", well);
            extras.put(component + ".foreground", text);
        }
        for (String component : new String[] {
                "List", "PopupMenu", "MenuItem", "Button", "ToggleButton", "Table" }) {
            extras.put(component + ".background", page);
            extras.put(component + ".foreground", text);
        }
        extras.put("ComboBox.buttonBackground", well);
        extras.put("ComboBox.buttonEditableBackground", well);
        extras.put("ComboBox.popupBackground", page);
        extras.put("ComboBox.selectionForeground", "#ffffff");
        extras.put("Spinner.buttonBackground", well);
        extras.put("Component.borderColor", hex(border()));
        extras.put("Component.disabledBorderColor", hex(border()));
        extras.put("Panel.background", page);
        extras.put("OptionPane.background", page);
        extras.put("RootPane.background", page);
        extras.put("TitlePane.background", page);
        extras.put("TableHeader.background", page);
        extras.put("Table.gridColor", hex(border()));
        extras.put("Table.selectionBackground", hex(alpha(PRIMARY, 255)));
        extras.put("Table.selectionForeground", "#ffffff");
        extras.put("Separator.foreground", hex(border()));
        extras.put("ToolTip.background", hex(darkMode ? new Color(0x12, 0x3B, 0x49) : INK));
        extras.put("ToolTip.foreground", "#ffffff");
        extras.put("Label.foreground", text);
        extras.put("CheckBox.foreground", text);
        extras.put("RadioButton.foreground", text);
        extras.put("TitledBorder.titleColor", text);
        extras.put("TableHeader.foreground", hex(muted()));
        extras.put("TextComponent.placeholderForeground", hex(muted()));
        com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(extras);
        if (darkMode) {
            com.formdev.flatlaf.FlatDarkLaf.setup();
        } else {
            com.formdev.flatlaf.FlatLightLaf.setup();
        }
    }

    public static Font regular(float size) {
        loadFonts();
        return regularFont.deriveFont(size);
    }

    public static Font semibold(float size) {
        loadFonts();
        return semiboldFont.deriveFont(size);
    }

    private static boolean alignmentFixInstalled;

    /**
     * EquipLink lays out pages with vertical BoxLayouts and expects every row to be
     * left-aligned. Swing defaults most components to centre alignment, and mixing the
     * two makes BoxLayout offset rows. This aligns children of vertical BoxLayouts left.
     */
    private static void installAlignmentFix() {
        if (alignmentFixInstalled) {
            return;
        }
        alignmentFixInstalled = true;
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() != java.awt.event.ContainerEvent.COMPONENT_ADDED) {
                return;
            }
            java.awt.event.ContainerEvent added = (java.awt.event.ContainerEvent) event;
            java.awt.Container parent = added.getContainer();
            if (!(parent instanceof javax.swing.JPanel)
                    || !(added.getChild() instanceof javax.swing.JComponent)
                    || !(parent.getLayout() instanceof javax.swing.BoxLayout)) {
                return;
            }
            int axis = ((javax.swing.BoxLayout) parent.getLayout()).getAxis();
            if (axis == javax.swing.BoxLayout.Y_AXIS || axis == javax.swing.BoxLayout.PAGE_AXIS) {
                ((javax.swing.JComponent) added.getChild())
                        .setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            }
        }, java.awt.AWTEvent.CONTAINER_EVENT_MASK);
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
        java.awt.GraphicsEnvironment environment =
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        environment.registerFont(regular);
        environment.registerFont(semibold);
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
