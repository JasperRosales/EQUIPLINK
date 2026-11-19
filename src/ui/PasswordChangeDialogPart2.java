
package src.ui;

import src.controller.AuthController;
import src.ui.components.PrimaryButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

/** Part 2 of PasswordChangeDialog, split out to keep files short. */
public abstract class PasswordChangeDialogPart2 extends PasswordChangeDialogPart1 {

    protected PasswordChangeDialogPart2(java.awt.Window owner, String title, java.awt.Dialog.ModalityType modality) {
        super(owner, title, modality);
    }


    // =========================================
    // GLASS CARD
    // =========================================

    static class GlassCard extends JPanel {

        GlassCard() {
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

            // Layered soft shadows
            for (int i = 7; i >= 1; i--) {

                g2.setColor(
                        new Color(
                                60,
                                10,
                                22,
                                5 + i * 3
                        )
                );

                g2.fillRoundRect(
                        5,
                        6 + i,
                        width - 12,
                        height - 17,
                        29,
                        29
                );
            }

            // Glass surface
            GradientPaint glass = new GradientPaint(
                    0, 0,
                    new Color(255, 255, 255, 242),
                    width, height,
                    new Color(255, 249, 242, 225)
            );

            g2.setPaint(glass);

            g2.fillRoundRect(
                    3,
                    3,
                    width - 11,
                    height - 15,
                    27,
                    27
            );

            // Glass outline
            g2.setColor(
                    new Color(255, 255, 255, 235)
            );

            g2.setStroke(
                    new BasicStroke(1.4f)
            );

            g2.drawRoundRect(
                    3,
                    3,
                    width - 11,
                    height - 15,
                    27,
                    27
            );

            // Top reflective line
            g2.setColor(
                    new Color(255, 255, 255, 180)
            );

            g2.drawLine(
                    30,
                    14,
                    width - 40,
                    14
            );

            g2.dispose();
        }
    }

    // =========================================
    // EXISTING PASSWORD UPDATE FUNCTION
    // =========================================

    void updatePassword() {

        char[] current =
                currentPassword.getPassword();

        char[] next =
                newPassword.getPassword();

        char[] confirmation =
                confirmPassword.getPassword();

        if (current.length == 0
                || next.length == 0
                || confirmation.length == 0) {

            clear(current, next, confirmation);

            UiSupport.showMessageDialog(
                    this,
                    "Complete all password fields.",
                    "Missing information",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        if (next.length < 8) {

            clear(current, next, confirmation);

            UiSupport.showMessageDialog(
                    this,
                    "The new password must be at least 8 characters.",
                    "Password too short",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        if (!Arrays.equals(next, confirmation)) {

            clear(current, next, confirmation);

            confirmPassword.setText("");

            UiSupport.showMessageDialog(
                    this,
                    "The new passwords do not match.",
                    "Password mismatch",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        saveButton.setEnabled(false);

        saveButton.setText("Updating password...");

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground()
                    throws Exception {

                authController.changePassword(
                        userId,
                        current,
                        next
                );

                return null;
            }

            @Override
            protected void done() {

                clear(
                        current,
                        next,
                        confirmation
                );

                currentPassword.setText("");
                newPassword.setText("");
                confirmPassword.setText("");

                saveButton.setEnabled(true);
                saveButton.setText("Update password");

                try {

                    get();

                    UiSupport.showMessageDialog(
                            PasswordChangeDialogPart2.this,
                            "Your password was updated.",
                            "Password updated",
                            JOptionPane.INFORMATION_MESSAGE
                    );

                    dispose();

                } catch (Exception exception) {

                    Throwable cause =
                            exception.getCause() == null
                                    ? exception
                                    : exception.getCause();

                    UiSupport.showMessageDialog(
                            PasswordChangeDialogPart2.this,
                            cause.getMessage(),
                            "Unable to update password",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }

        }.execute();
    }

    // =========================================
    // CLEAR PASSWORD ARRAYS
    // =========================================

    void clear(char[]... secrets) {

        for (char[] secret : secrets) {

            Arrays.fill(secret, '\0');
        }
    }
}
