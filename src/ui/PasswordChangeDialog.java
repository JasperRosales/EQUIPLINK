
package src.ui;

import src.controller.AuthController;
import src.ui.components.PrimaryButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

public class PasswordChangeDialog extends JDialog {

    // =========================================
    // EXISTING FIELDS AND CONTROLLERS
    // =========================================

    private final long userId;
    private final AuthController authController;

    private final JPasswordField currentPassword =
            new GlassPasswordField();

    private final JPasswordField newPassword =
            new GlassPasswordField();

    private final JPasswordField confirmPassword =
            new GlassPasswordField();

    private final PrimaryButton saveButton =
            new PrimaryButton("Update password");

    // =========================================
    // GLASSMORPHISM COLORS
    // =========================================

    private static final Color NAVY =
            new Color(28, 49, 82);

    private static final Color MUTED =
            new Color(112, 130, 155);

    private static final Color PRIMARY =
            new Color(52, 112, 190);

    private static final Color BORDER =
            new Color(207, 219, 236);

    private static final Color BACKGROUND_TOP =
            new Color(239, 246, 255);

    private static final Color BACKGROUND_BOTTOM =
            new Color(218, 232, 249);

    // =========================================
    // CONSTRUCTOR
    // =========================================

    public PasswordChangeDialog(
            Window owner,
            long userId,
            AuthController authController) {

        super(
                owner,
                "Change password",
                Dialog.ModalityType.APPLICATION_MODAL
        );

        this.userId = userId;
        this.authController = authController;

        buildContent();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setMinimumSize(new Dimension(540, 560));
        setSize(560, 590);

        setLocationRelativeTo(owner);
    }

    // =========================================
    // MAIN CONTENT
    // =========================================

    private void buildContent() {

        GlassBackgroundPanel root =
                new GlassBackgroundPanel();

        root.setLayout(new GridBagLayout());

        // Main glass card
        GlassCard card = new GlassCard();

        card.setLayout(
                new BoxLayout(card, BoxLayout.Y_AXIS)
        );

        card.setBorder(
                new EmptyBorder(28, 32, 28, 32)
        );

        card.setPreferredSize(
                new Dimension(490, 500)
        );

        // =====================================
        // HEADER
        // =====================================

        JLabel eyebrow = new JLabel(
                "ACCOUNT SECURITY"
        );

        eyebrow.setFont(
                AppTheme.semibold(10.5f)
        );

        eyebrow.setForeground(PRIMARY);

        eyebrow.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        JLabel title = new JLabel(
                "Change your password"
        );

        title.setFont(
                AppTheme.semibold(23)
        );

        title.setForeground(NAVY);

        title.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        JLabel hint = new JLabel(
                "Use at least 8 characters for your new password."
        );

        hint.setFont(
                AppTheme.regular(11)
        );

        hint.setForeground(MUTED);

        hint.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        card.add(eyebrow);

        card.add(
                Box.createVerticalStrut(8)
        );

        card.add(title);

        card.add(
                Box.createVerticalStrut(9)
        );

        card.add(hint);

        card.add(
                Box.createVerticalStrut(28)
        );

        // =====================================
        // PASSWORD FIELDS
        // =====================================

        addPasswordField(
                card,
                "Current password",
                currentPassword
        );

        addPasswordField(
                card,
                "New password",
                newPassword
        );

        addPasswordField(
                card,
                "Confirm new password",
                confirmPassword
        );

        card.add(
                Box.createVerticalStrut(8)
        );

        // =====================================
        // ACTION BUTTONS
        // =====================================

        PrimaryButton cancel = new PrimaryButton(
                "Cancel",
                PrimaryButton.Style.SECONDARY
        );

        cancel.setFont(
                AppTheme.semibold(11)
        );

        cancel.setPreferredSize(
                new Dimension(125, 44)
        );

        saveButton.setFont(
                AppTheme.semibold(11)
        );

        saveButton.setPreferredSize(
                new Dimension(165, 44)
        );

        // EXISTING CANCEL FUNCTION
        cancel.addActionListener(
                event -> dispose()
        );

        // EXISTING PASSWORD UPDATE FUNCTION
        saveButton.addActionListener(
                event -> updatePassword()
        );

        JPanel actions = new JPanel(
                new FlowLayout(
                        FlowLayout.RIGHT,
                        10,
                        0
                )
        );

        actions.setOpaque(false);

        actions.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        actions.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 50)
        );

        actions.add(cancel);
        actions.add(saveButton);

        card.add(actions);

        // =====================================
        // CENTER GLASS CARD
        // =====================================

        GridBagConstraints constraints =
                new GridBagConstraints();

        constraints.gridx = 0;
        constraints.gridy = 0;

        constraints.weightx = 1;
        constraints.weighty = 1;

        constraints.anchor =
                GridBagConstraints.CENTER;

        root.add(card, constraints);

        setContentPane(root);
    }

    // =========================================
    // PASSWORD FIELD DESIGN
    // =========================================

    private void addPasswordField(
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

    private static class GlassPasswordField
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
                    new Color(237, 244, 253, 235)
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
                        new Color(65, 135, 215, 155)
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

    private static class GlassBackgroundPanel
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
                    new Color(90, 145, 210, 30)
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

    // =========================================
    // GLASS CARD
    // =========================================

    private static class GlassCard extends JPanel {

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
                                35,
                                60,
                                95,
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
                    new Color(240, 247, 255, 225)
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

    private void updatePassword() {

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

            JOptionPane.showMessageDialog(
                    this,
                    "Complete all password fields.",
                    "Missing information",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        if (next.length < 8) {

            clear(current, next, confirmation);

            JOptionPane.showMessageDialog(
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

            JOptionPane.showMessageDialog(
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

                    JOptionPane.showMessageDialog(
                            PasswordChangeDialog.this,
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

                    JOptionPane.showMessageDialog(
                            PasswordChangeDialog.this,
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

    private void clear(char[]... secrets) {

        for (char[] secret : secrets) {

            Arrays.fill(secret, '\0');
        }
    }
}
