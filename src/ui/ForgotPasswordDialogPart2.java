
package src.ui;

import src.controller.AuthController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Part 2 of ForgotPasswordDialog, split out to keep files short. */
public abstract class ForgotPasswordDialogPart2 extends ForgotPasswordDialogPart1 {

    protected ForgotPasswordDialogPart2(java.awt.Window owner, String title, java.awt.Dialog.ModalityType modality) {
        super(owner, title, modality);
    }


    // =========================================
    // INPUT FIELD COMPONENTS
    // =========================================

    void addField(
            JPanel panel,
            String labelText,
            JComponent field) {

        JLabel label = new JLabel(labelText);

        label.setFont(AppTheme.semibold(10));
        label.setForeground(NAVY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        field.setPreferredSize(
                new Dimension(460, 46)
        );

        field.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 46)
        );

        field.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(label);
        panel.add(Box.createVerticalStrut(8));
        panel.add(field);
        panel.add(Box.createVerticalStrut(16));
    }

    // =========================================
    // 3D BACKGROUND
    // =========================================

    static class SoftBackgroundPanel extends JPanel {

        SoftBackgroundPanel() {
            setOpaque(false);
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

            GradientPaint gradient = new GradientPaint(
                    0, 0,
                    BACKGROUND_TOP,
                    width, height,
                    BACKGROUND_BOTTOM
            );

            g2.setPaint(gradient);

            g2.fillRect(
                    0, 0,
                    width, height
            );

            // Soft dimensional circles
            g2.setColor(
                    new Color(255, 255, 255, 75)
            );

            g2.fillOval(
                    width - 160,
                    35,
                    190,
                    190
            );

            g2.setColor(
                    AppTheme.alpha(AppTheme.PRIMARY_LIGHT, 35)
            );

            g2.fillOval(
                    20,
                    height - 160,
                    190,
                    190
            );

            // Subtle light reflection
            g2.setColor(
                    new Color(255, 255, 255, 45)
            );

            g2.fillOval(
                    width / 2 - 30,
                    height / 2 - 140,
                    65,
                    65
            );

            g2.dispose();
        }
    }

    // =========================================
    // 3D ELEVATED CARD
    // =========================================

    static class SoftCard extends JPanel {

        SoftCard() {
            setOpaque(false);
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

            // Layered shadow
            for (int i = 8; i >= 1; i--) {

                g2.setColor(
                        new Color(
                                70,
                                14,
                                28,
                                4 + i * 2
                        )
                );

                g2.fillRoundRect(
                        5,
                        6 + i,
                        width - 12,
                        height - 17,
                        30,
                        30
                );
            }

            // White soft ambient highlight
            g2.setColor(
                    new Color(255, 255, 255, 100)
            );

            g2.fillRoundRect(
                    1,
                    1,
                    width - 9,
                    height - 12,
                    29,
                    29
            );

            // Raised main surface
            GradientPaint surfaceGradient =
                    new GradientPaint(
                            0, 0,
                            new Color(255, 255, 255),
                            width, height,
                            new Color(255, 249, 242)
                    );

            g2.setPaint(surfaceGradient);

            g2.fillRoundRect(
                    4,
                    4,
                    width - 14,
                    height - 17,
                    27,
                    27
            );

            // Outer soft edge
            g2.setColor(
                    new Color(255, 255, 255, 230)
            );

            g2.setStroke(new BasicStroke(1.4f));

            g2.drawRoundRect(
                    4,
                    4,
                    width - 14,
                    height - 17,
                    27,
                    27
            );

            // Upper reflective line
            g2.setColor(
                    new Color(255, 255, 255, 190)
            );

            g2.drawLine(
                    34,
                    15,
                    width - 42,
                    15
            );

            g2.dispose();
        }
    }

    // =========================================
    // BUTTON AND FIELD STATE
    // =========================================

    void setBusy(boolean busy) {

        requestButton.setEnabled(!busy);
        resetButton.setEnabled(!busy);

        identity.setEnabled(!busy);
        registeredEmail.setEnabled(!busy);
        newPassword.setEnabled(!busy);
        confirmPassword.setEnabled(!busy);
    }

    // =========================================
    // MESSAGES
    // =========================================

    void showMessage(
            String message,
            String title) {

        UiSupport.showMessageDialog(
                this,
                message,
                title,
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    void showFailure(
            Exception exception,
            String title) {

        Throwable cause =
                exception instanceof ExecutionException
                        && exception.getCause() != null
                        ? exception.getCause()
                        : exception;

        UiSupport.showMessageDialog(
                this,
                cause.getMessage(),
                title,
                JOptionPane.ERROR_MESSAGE
        );
    }
}
