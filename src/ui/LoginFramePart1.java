
package src.ui;

import src.controller.AuthController;
import src.model.User;
import src.ui.components.AuthBrandPanel;
import src.ui.components.PrimaryButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.function.Consumer;

/** Part 1 of LoginFrame, split out to keep files short. */
public abstract class LoginFramePart1 extends JFrame {

    final JTextField username = new JTextField();
    final JPasswordField password = new JPasswordField();
    final PrimaryButton loginButton = new PrimaryButton("Log in");

    AuthController authController;
    Consumer<User> onLoginSuccess;

    // Glass UI colors
    static final Color GLASS_WHITE =
            new Color(255, 255, 255, 215);

    static final Color GLASS_BORDER =
            new Color(255, 255, 255, 220);

    static final Color INPUT_BACKGROUND =
            new Color(255, 255, 255, 235);

    static final Color NAVY = AppTheme.INK;

    // =========================================
    // GLASS INPUT FIELDS
    // =========================================

    void addField(
            JPanel panel,
            String label,
            JComponent field) {

        JLabel fieldLabel = new JLabel(label);

        fieldLabel.setFont(AppTheme.semibold(10));
        fieldLabel.setForeground(AppTheme.foreground());
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        styleGlassInput(field);

        field.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 45)
        );

        field.setPreferredSize(new Dimension(330, 45));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(fieldLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(field);
        panel.add(Box.createVerticalStrut(17));
    }

    void styleGlassInput(JComponent field) {

        field.setFont(AppTheme.regular(13));
        field.setForeground(AppTheme.foreground());
        field.setBackground(AppTheme.surface());

        field.setFocusable(true);
    }

    // =========================================
    // LOGIN FUNCTION
    // =========================================

    void doLogin() {

        char[] pass = password.getPassword();
        String enteredUsername = username.getText().trim();

        if (enteredUsername.isEmpty() || pass.length == 0) {

            Arrays.fill(pass, '\0');

            UiSupport.showMessageDialog(
                    this,
                    "Please enter your username and password.",
                    "Missing information",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Signing in...");

        new SwingWorker<User, Void>() {

            @Override
            protected User doInBackground() throws Exception {

                return authController.login(
                        enteredUsername,
                        pass
                );
            }

            @Override
            protected void done() {

                Arrays.fill(pass, '\0');
                password.setText("");

                loginButton.setEnabled(true);
                loginButton.setText("Log in");

                try {

                    User user = get();

                    if (user == null) {

                        UiSupport.showMessageDialog(
                                LoginFramePart1.this,
                                "Invalid login details or inactive account.",
                                "Login failed",
                                JOptionPane.WARNING_MESSAGE
                        );

                        return;
                    }

                    dispose();
                    onLoginSuccess.accept(user);

                } catch (Exception exception) {

                    Throwable cause = exception.getCause() == null
                            ? exception
                            : exception.getCause();

                    UiSupport.showMessageDialog(
                            LoginFramePart1.this,
                            cause.getMessage(),
                            "Login error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }

        }.execute();
    }

    // =========================================
    // LINK BUTTON STYLE
    // =========================================

    void styleLink(JButton button) {

        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);

        button.setForeground(AppTheme.primary());
        button.setFocusPainted(false);

        button.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        );

        button.setFont(AppTheme.regular(10.5f));
    }

    // =========================================
    // GLASS BACKGROUND
    // =========================================

    static class GlassBackgroundPanel extends JPanel {

        GlassBackgroundPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {

            super.paintComponent(graphics);

            Graphics2D g2 = (Graphics2D) graphics.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int width = getWidth();
            int height = getHeight();

            g2.setColor(AppTheme.background());
            g2.fillRect(0, 0, width, height);

            g2.dispose();
        }
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

            Graphics2D g2 = (Graphics2D) graphics.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int width = getWidth();
            int height = getHeight();

            int arc = 24;
            src.ui.components.Neu.raised(g2, 6, 6, width - 12, height - 12, arc, 5f,
                    AppTheme.surface());

            g2.dispose();

            super.paintComponent(graphics);
        }
    }
}
