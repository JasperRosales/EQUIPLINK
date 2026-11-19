
package src.ui;

import src.controller.AuthController;
import src.ui.components.PrimaryButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

/** Part 1 of PasswordChangeDialog, split out to keep files short. */
public abstract class PasswordChangeDialogPart1 extends JDialog {

    protected PasswordChangeDialogPart1(java.awt.Window owner, String title, java.awt.Dialog.ModalityType modality) {
        super(owner, title, modality);
    }


    // =========================================
    // EXISTING FIELDS AND CONTROLLERS
    // =========================================

    long userId;
    AuthController authController;

    final JPasswordField currentPassword =
            new GlassPasswordField();

    final JPasswordField newPassword =
            new GlassPasswordField();

    final JPasswordField confirmPassword =
            new GlassPasswordField();

    final PrimaryButton saveButton =
            new PrimaryButton("Update password");

    // =========================================
    // GLASSMORPHISM COLORS
    // =========================================

    static final Color NAVY = AppTheme.INK;

    static final Color MUTED = AppTheme.INK_MUTED;

    static final Color PRIMARY = AppTheme.PRIMARY;

    static final Color BORDER = AppTheme.LIGHT_BORDER;

    static final Color BACKGROUND_TOP = AppTheme.mix(AppTheme.LIGHT_BG, Color.WHITE, 0.4f);

    static final Color BACKGROUND_BOTTOM = AppTheme.LIGHT_BG_DEEP;

    // =========================================
    // PASSWORD FIELD DESIGN
    // =========================================

    void addPasswordField(
            JPanel panel,
            String labelText,
            JPasswordField field) {

        JLabel label = new JLabel(labelText);

        label.setFont(
                AppTheme.semibold(10)
        );

        label.setForeground(NAVY);

        label.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        field.setFont(
                AppTheme.regular(12)
        );

        field.setPreferredSize(
                new Dimension(420, 44)
        );

        field.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 44)
        );

        field.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        panel.add(label);

        panel.add(
                Box.createVerticalStrut(7)
        );

        panel.add(field);

        panel.add(
                Box.createVerticalStrut(17)
        );
    }

    // =========================================
    // GLASS PASSWORD FIELD
    // =========================================

    static class GlassPasswordField
            extends JPasswordField {

        GlassPasswordField() {

            setFont(
                    AppTheme.regular(12)
            );

            setForeground(NAVY);

            setCaretColor(PRIMARY);

            setOpaque(false);

            setBorder(
                    new EmptyBorder(11, 15, 11, 15)
            );

            setFocusable(true);
        }

        @Override
        protected void paintComponent(Graphics graphics) {

            Graphics2D g2 =
                    (Graphics2D) graphics.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int width = getWidth();
            int height = getHeight();

            // Input background gradient
            GradientPaint gradient = new GradientPaint(
                    0, 0,
                    new Color(255, 255, 255, 245),
                    0, height,
                    new Color(255, 249, 242, 235)
            );

            g2.setPaint(gradient);

            g2.fillRoundRect(
                    1,
                    1,
                    width - 3,
                    height - 3,
                    14,
                    14
            );

            // Soft border
            g2.setColor(BORDER);

            g2.setStroke(
                    new BasicStroke(1f)
            );

            g2.drawRoundRect(
                    1,
                    1,
                    width - 3,
                    height - 3,
                    14,
                    14
            );

            // Top highlight
            g2.setColor(
                    new Color(255, 255, 255, 220)
            );

            g2.drawLine(
                    15,
                    3,
                    width - 16,
                    3
            );

            // Focus effect
            if (hasFocus()) {

                g2.setColor(
                        AppTheme.alpha(AppTheme.PRIMARY, 155)
                );

                g2.setStroke(
                        new BasicStroke(1.5f)
                );

                g2.drawRoundRect(
                        1,
                        1,
                        width - 3,
                        height - 3,
                        14,
                        14
                );
            }

            g2.dispose();

            super.paintComponent(graphics);
        }
    }

    // =========================================
    // GLASS BACKGROUND
    // =========================================

    static class GlassBackgroundPanel
            extends JPanel {

        GlassBackgroundPanel() {
            setOpaque(true);
            setBackground(BACKGROUND_TOP);
        }

        @Override
        protected void paintComponent(Graphics graphics) {

            super.paintComponent(graphics);

            Graphics2D g2 =
                    (Graphics2D) graphics.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int width = getWidth();
            int height = getHeight();

            // Main gradient
            GradientPaint gradient = new GradientPaint(
                    0, 0,
                    BACKGROUND_TOP,
                    width, height,
                    BACKGROUND_BOTTOM
            );

            g2.setPaint(gradient);

            g2.fillRect(
                    0,
                    0,
                    width,
                    height
            );

            // Decorative upper circle
            g2.setColor(
                    new Color(255, 255, 255, 75)
            );

            g2.fillOval(
                    width - 155,
                    35,
                    190,
                    190
            );

            // Decorative bottom circle
            g2.setColor(
                    AppTheme.alpha(AppTheme.PRIMARY_LIGHT, 30)
            );

            g2.fillOval(
                    15,
                    height - 170,
                    195,
                    195
            );

            // Subtle light reflection
            g2.setColor(
                    new Color(255, 255, 255, 45)
            );

            g2.fillOval(
                    width / 2,
                    height / 2 - 130,
                    65,
                    65
            );

            g2.dispose();
        }
    }
}
