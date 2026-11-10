package src.ui;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppTheme {

    public static final Color BG = new Color(244, 246, 250);
    public static final Color NAVY = new Color(15, 23, 42);
    public static final Color BLUE = new Color(37, 99, 235);

    private static final Color SIDEBAR_NAVY = new Color(15, 23, 42);
    private static final Color PAGE = new Color(244, 246, 250);
    private static final Color DARK_PAGE = new Color(11, 17, 32);
    private static Font regularFont;
    private static Font semiboldFont;
    private static boolean darkMode;

    private AppTheme() {
    }

    public static Color background() {
        return darkMode ? DARK_PAGE : PAGE;
    }

    public static Color surface() {
        return darkMode ? new Color(22, 31, 50) : Color.WHITE;
    }

    public static Color elevatedSurface() {
        return darkMode ? new Color(30, 41, 62) : new Color(248, 250, 252);
    }

    public static Color foreground() {
        return darkMode ? new Color(248, 250, 252) : new Color(15, 23, 42);
    }

    public static Color muted() {
        return darkMode ? new Color(203, 213, 225) : new Color(100, 116, 139);
    }

    public static Color border() {
        return darkMode ? new Color(45, 58, 82) : new Color(226, 232, 240);
    }

    public static Color primary() {
        return BLUE;
    }

    public static Color sidebar() {
        return SIDEBAR_NAVY;
    }

    public static Color success() {
        return new Color(22, 163, 74);
    }

    public static Color warning() {
        return new Color(217, 119, 6);
    }

    public static Color danger() {
        return new Color(220, 38, 38);
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
        javax.swing.UIManager.put("@accentColor", "#2563eb");
        javax.swing.UIManager.put("Component.arc", 10);
        javax.swing.UIManager.put("Button.arc", 10);
        javax.swing.UIManager.put("TextComponent.arc", 10);
        javax.swing.UIManager.put("CheckBox.arc", 6);
        javax.swing.UIManager.put("Component.focusWidth", 1);
        javax.swing.UIManager.put("Component.innerFocusWidth", 0);
        javax.swing.UIManager.put("Component.focusColor", new Color(37, 99, 235, 90));
        javax.swing.UIManager.put("Component.focusedBorderColor", BLUE);
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
        if (darkMode) {
            // Match EquipLink's slate surfaces and keep all text white in dark mode.
            String surface = "#161f32";
            String text = "#f8fafc";
            for (String component : new String[] {
                    "ComboBox", "TextField", "PasswordField", "FormattedTextField",
                    "TextArea", "Spinner", "List", "PopupMenu", "MenuItem", "Button",
                    "ToggleButton", "Table", "EditorPane", "TextPane" }) {
                extras.put(component + ".background", surface);
                extras.put(component + ".foreground", text);
            }
            extras.put("ComboBox.buttonBackground", surface);
            extras.put("ComboBox.buttonEditableBackground", surface);
            extras.put("ComboBox.popupBackground", surface);
            extras.put("ComboBox.selectionForeground", text);
            extras.put("Spinner.buttonBackground", surface);
            extras.put("Component.borderColor", "#2d3a52");
            extras.put("Component.disabledBorderColor", "#2d3a52");
            extras.put("Panel.background", "#0b1120");
            extras.put("OptionPane.background", surface);
            extras.put("Label.foreground", text);
            extras.put("CheckBox.foreground", text);
            extras.put("RadioButton.foreground", text);
            extras.put("TitledBorder.titleColor", text);
            extras.put("TableHeader.foreground", text);
            extras.put("TextComponent.placeholderForeground", "#94a3b8");
        }
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
