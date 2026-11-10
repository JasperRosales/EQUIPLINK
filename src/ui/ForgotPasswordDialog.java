
package src.ui;

import src.controller.AuthController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class ForgotPasswordDialog extends JDialog {

    private final AuthController authController;

    private final JTextField identity = new SoftTextField();
    private final JPasswordField newPassword = new SoftPasswordField();
    private final JPasswordField confirmPassword = new SoftPasswordField();

    private final Soft3DButton requestButton =
            new Soft3DButton("Request ICT approval", true);

    private final Soft3DButton resetButton =
            new Soft3DButton("Set new password", false);

    // =========================================
    // EQUIPLINK 3D SOFT REALISM COLORS
    // =========================================

    private static final Color BACKGROUND_TOP = AppTheme.mix(AppTheme.LIGHT_BG, Color.WHITE, 0.4f);

    private static final Color BACKGROUND_BOTTOM = AppTheme.LIGHT_BG_DEEP;

    private static final Color NAVY = AppTheme.INK;

    private static final Color MUTED = AppTheme.INK_MUTED;

    private static final Color PRIMARY = AppTheme.PRIMARY;

    private static final Color PRIMARY_DARK = AppTheme.PRIMARY_DARK;

    private static final Color BORDER = AppTheme.LIGHT_BORDER;

    // =========================================
    // CONSTRUCTOR
    // =========================================

    public ForgotPasswordDialog(
            Window owner,
            AuthController authController) {

        super(owner, "Forgot password", ModalityType.APPLICATION_MODAL);

        this.authController = authController;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(620, 620));
        setSize(640, 640);
        setResizable(false);

        setContentPane(buildContent());
        setLocationRelativeTo(owner);
    }

    // =========================================
    // MAIN CONTENT
    // =========================================

    private JPanel buildContent() {

        SoftBackgroundPanel background =
                new SoftBackgroundPanel();

        background.setLayout(new GridBagLayout());

        // Main elevated card
        SoftCard card = new SoftCard();

        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        card.setBorder(
                new EmptyBorder(30, 34, 30, 34)
        );

        card.setPreferredSize(
                new Dimension(550, 535)
        );

        // =====================================
        // HEADER
        // =====================================

        JLabel eyebrow = new JLabel("ACCOUNT SECURITY");

        eyebrow.setFont(AppTheme.semibold(10.5f));
        eyebrow.setForeground(PRIMARY);
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = new JLabel("Password recovery");

        heading.setFont(AppTheme.semibold(24));
        heading.setForeground(NAVY);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        // FIXED DESCRIPTION:
        // JTextArea automatically wraps the text.
        // No HTML fixed-width clipping.

        JTextArea explanation = new JTextArea(
                "Submit a request for ICT review. After ICT approves it, "
                + "return here and set your new password using the same "
                + "username or email."
        );

        explanation.setFont(AppTheme.regular(11));
        explanation.setForeground(MUTED);
        explanation.setOpaque(false);
        explanation.setEditable(false);
        explanation.setFocusable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setRows(3);
        explanation.setColumns(45);

        explanation.setBorder(
                new EmptyBorder(0, 0, 0, 0)
        );

        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);

        explanation.setPreferredSize(
                new Dimension(480, 52)
        );

        explanation.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 58)
        );

        // Add header components
        card.add(eyebrow);
        card.add(Box.createVerticalStrut(8));

        card.add(heading);
        card.add(Box.createVerticalStrut(9));

        card.add(explanation);
        card.add(Box.createVerticalStrut(24));

        // =====================================
        // INPUT FIELDS
        // =====================================

        addField(
                card,
                "Username or email",
                identity
        );

        addField(
                card,
                "New password (at least 8 characters)",
                newPassword
        );

        addField(
                card,
                "Confirm new password",
                confirmPassword
        );

        card.add(Box.createVerticalStrut(5));

        // =====================================
        // ACTION BUTTONS
        // =====================================

        requestButton.addActionListener(
                event -> requestApproval()
        );

        resetButton.addActionListener(
                event -> resetPassword()
        );

        JPanel actions = new JPanel(
                new GridLayout(1, 2, 14, 0)
        );

        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        actions.setPreferredSize(
                new Dimension(480, 50)
        );

        actions.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 50)
        );

        actions.add(requestButton);
        actions.add(resetButton);

        card.add(actions);

        // =====================================
        // CENTER GLASS / 3D CARD
        // =====================================

        GridBagConstraints constraints =
                new GridBagConstraints();

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.CENTER;

        background.add(card, constraints);

        return background;
    }

    // =========================================
    // INPUT FIELD COMPONENTS
    // =========================================

    private void addField(
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
    // SOFT 3D TEXT FIELD
    // =========================================

    private static class SoftTextField extends JTextField {

        SoftTextField() {

            setFont(AppTheme.regular(11));
            setForeground(NAVY);
            setCaretColor(PRIMARY);
            setOpaque(false);

            setBorder(
                    new EmptyBorder(12, 15, 12, 15)
            );
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

            // Soft inset surface
            GradientPaint gradient = new GradientPaint(
                    0, 0,
                    new Color(255, 255, 255),
                    0, height,
                    new Color(237, 243, 251)
            );

            g2.setPaint(gradient);

            g2.fillRoundRect(
                    1, 1,
                    width - 3,
                    height - 3,
                    14, 14
            );

            // Outer border
            g2.setColor(BORDER);

            g2.drawRoundRect(
                    1, 1,
                    width - 3,
                    height - 3,
                    14, 14
            );

            // Top highlight
            g2.setColor(
                    new Color(255, 255, 255, 210)
            );

            g2.drawLine(
                    15, 3,
                    width - 16, 3
            );

            // Bottom inset shadow
            g2.setColor(
                    new Color(170, 190, 218, 85)
            );

            g2.drawLine(
                    15, height - 3,
                    width - 16, height - 3
            );

            // Focus highlight
            if (hasFocus()) {

                g2.setColor(
                        AppTheme.alpha(AppTheme.PRIMARY, 150)
                );

                g2.setStroke(new BasicStroke(1.5f));

                g2.drawRoundRect(
                        1, 1,
                        width - 3,
                        height - 3,
                        14, 14
                );
            }

            g2.dispose();

            super.paintComponent(graphics);
        }
    }

    // =========================================
    // SOFT 3D PASSWORD FIELD
    // =========================================

    private static class SoftPasswordField extends JPasswordField {

        SoftPasswordField() {

            setFont(AppTheme.regular(11));
            setForeground(NAVY);
            setCaretColor(PRIMARY);
            setOpaque(false);

            setBorder(
                    new EmptyBorder(12, 15, 12, 15)
            );
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

            GradientPaint gradient = new GradientPaint(
                    0, 0,
                    new Color(255, 255, 255),
                    0, height,
                    new Color(237, 243, 251)
            );

            g2.setPaint(gradient);

            g2.fillRoundRect(
                    1, 1,
                    width - 3,
                    height - 3,
                    14, 14
            );

            g2.setColor(BORDER);

            g2.drawRoundRect(
                    1, 1,
                    width - 3,
                    height - 3,
                    14, 14
            );

            g2.setColor(
                    new Color(255, 255, 255, 210)
            );

            g2.drawLine(
                    15, 3,
                    width - 16, 3
            );

            g2.setColor(
                    new Color(170, 190, 218, 85)
            );

            g2.drawLine(
                    15, height - 3,
                    width - 16, height - 3
            );

            if (hasFocus()) {

                g2.setColor(
                        AppTheme.alpha(AppTheme.PRIMARY, 150)
                );

                g2.setStroke(new BasicStroke(1.5f));

                g2.drawRoundRect(
                        1, 1,
                        width - 3,
                        height - 3,
                        14, 14
                );
            }

            g2.dispose();

            super.paintComponent(graphics);
        }
    }

    // =========================================
    // 3D SOFT BUTTON
    // =========================================

    private static class Soft3DButton extends JButton {

        private final boolean primary;

        Soft3DButton(String text, boolean primary) {

            super(text);

            this.primary = primary;

            setFont(AppTheme.semibold(11));

            setForeground(
                    primary ? Color.WHITE : NAVY
            );

            setHorizontalAlignment(SwingConstants.CENTER);

            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);

            setRolloverEnabled(true);

            setBorder(
                    new EmptyBorder(12, 12, 12, 12)
            );

            setCursor(
                    Cursor.getPredefinedCursor(
                            Cursor.HAND_CURSOR
                    )
            );
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

            boolean pressed = getModel().isPressed();
            boolean hovered = getModel().isRollover();
            boolean enabled = isEnabled();

            int offset = pressed ? 3 : 0;

            // Bottom depth / shadow
            if (!pressed && enabled) {

                g2.setColor(
                        primary
                                ? AppTheme.alpha(AppTheme.PRIMARY_DARK, 100)
                                : new Color(139, 158, 184, 100)
                );

                g2.fillRoundRect(
                        3, 5,
                        width - 7,
                        height - 8,
                        17, 17
                );
            }

            // Main button surface
            Color top;
            Color bottom;

            if (!enabled) {

                top = new Color(207, 216, 229);
                bottom = new Color(190, 202, 219);

            } else if (primary) {

                top = hovered
                        ? AppTheme.PRIMARY_LIGHT
                        : AppTheme.PRIMARY;

                bottom = pressed
                        ? PRIMARY_DARK
                        : AppTheme.PRIMARY_DARK;

            } else {

                top = hovered
                        ? new Color(255, 255, 255)
                        : new Color(251, 253, 255);

                bottom = pressed
                        ? new Color(218, 229, 245)
                        : new Color(231, 239, 250);
            }

            GradientPaint gradient = new GradientPaint(
                    0, 2 + offset,
                    top,
                    0, height - 5 + offset,
                    bottom
            );

            g2.setPaint(gradient);

            g2.fillRoundRect(
                    2, 2 + offset,
                    width - 5,
                    height - 7,
                    16, 16
            );

            // Surface outline
            g2.setColor(
                    primary
                            ? AppTheme.PRIMARY_DARK
                            : new Color(211, 224, 241)
            );

            g2.drawRoundRect(
                    2, 2 + offset,
                    width - 5,
                    height - 7,
                    16, 16
            );

            // Top reflective edge
            if (enabled && !pressed) {

                g2.setColor(
                        primary
                                ? new Color(255, 255, 255, 75)
                                : new Color(255, 255, 255, 230)
                );

                g2.setStroke(new BasicStroke(1.2f));

                g2.drawLine(
                        16, 5,
                        width - 17, 5
                );
            }

            g2.dispose();

            super.paintComponent(graphics);
        }
    }

    // =========================================
    // 3D BACKGROUND
    // =========================================

    private static class SoftBackgroundPanel extends JPanel {

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

    private static class SoftCard extends JPanel {

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
                                49,
                                72,
                                105,
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
                            new Color(239, 245, 253)
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
    // REQUEST ICT APPROVAL
    // =========================================

    private void requestApproval() {

        String enteredIdentity =
                identity.getText().trim();

        if (enteredIdentity.isEmpty()) {

            showMessage(
                    "Enter your username or email first.",
                    "Missing information"
            );

            return;
        }

        setBusy(true);

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {

                authController.requestPasswordReset(
                        enteredIdentity
                );

                return null;
            }

            @Override
            protected void done() {

                setBusy(false);

                try {

                    get();

                    showMessage(
                            "If the account is registered, its reset request "
                                    + "is now waiting for ICT approval.",
                            "Request submitted"
                    );

                } catch (Exception exception) {

                    showFailure(
                            exception,
                            "Unable to submit request"
                    );
                }
            }

        }.execute();
    }

    // =========================================
    // RESET PASSWORD
    // =========================================

    private void resetPassword() {

        String enteredIdentity =
                identity.getText().trim();

        char[] password = newPassword.getPassword();
        char[] confirmation = confirmPassword.getPassword();

        if (enteredIdentity.isEmpty()) {

            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');

            showMessage(
                    "Enter your username or email first.",
                    "Missing information"
            );

            return;
        }

        if (!Arrays.equals(password, confirmation)) {

            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');

            showMessage(
                    "The passwords do not match.",
                    "Check your password"
            );

            return;
        }

        Arrays.fill(confirmation, '\0');

        setBusy(true);

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {

                authController.resetPasswordAfterApproval(
                        enteredIdentity,
                        password
                );

                return null;
            }

            @Override
            protected void done() {

                Arrays.fill(password, '\0');

                newPassword.setText("");
                confirmPassword.setText("");

                setBusy(false);

                try {

                    get();

                    showMessage(
                            "Your password was changed. You can now sign in "
                                    + "with the new password.",
                            "Password updated"
                    );

                    dispose();

                } catch (Exception exception) {

                    showFailure(
                            exception,
                            "Unable to change password"
                    );
                }
            }

        }.execute();
    }

    // =========================================
    // BUTTON AND FIELD STATE
    // =========================================

    private void setBusy(boolean busy) {

        requestButton.setEnabled(!busy);
        resetButton.setEnabled(!busy);

        identity.setEnabled(!busy);
        newPassword.setEnabled(!busy);
        confirmPassword.setEnabled(!busy);
    }

    // =========================================
    // MESSAGES
    // =========================================

    private void showMessage(
            String message,
            String title) {

        JOptionPane.showMessageDialog(
                this,
                message,
                title,
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void showFailure(
            Exception exception,
            String title) {

        Throwable cause =
                exception instanceof ExecutionException
                        && exception.getCause() != null
                        ? exception.getCause()
                        : exception;

        JOptionPane.showMessageDialog(
                this,
                cause.getMessage(),
                title,
                JOptionPane.ERROR_MESSAGE
        );
    }
}
