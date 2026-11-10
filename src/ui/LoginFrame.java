
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

public class LoginFrame extends JFrame {

    private final JTextField username = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final PrimaryButton loginButton = new PrimaryButton("Log in");

    private final AuthController authController;
    private final Consumer<User> onLoginSuccess;

    // Glass UI colors
    private static final Color GLASS_WHITE =
            new Color(255, 255, 255, 215);

    private static final Color GLASS_BORDER =
            new Color(255, 255, 255, 220);

    private static final Color INPUT_BACKGROUND =
            new Color(255, 255, 255, 235);

    private static final Color NAVY =
            new Color(28, 49, 82);

    public LoginFrame(
            AuthController authController,
            Consumer<User> onLoginSuccess) {

        this.authController = authController;
        this.onLoginSuccess = onLoginSuccess;

        setTitle("EquipLink | Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setSize(1040, 670);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.background());

        root.add(
                new AuthBrandPanel(
                        "Equipment that keeps campus moving.",
                        "Request the resources you need, follow your request status, and keep every campus activity organized."
                ),
                BorderLayout.WEST
        );

        root.add(createLoginForm(), BorderLayout.CENTER);

        setContentPane(root);
    }

    // =========================================
    // GLASS LOGIN FORM
    // =========================================

    private JPanel createLoginForm() {

        GlassBackgroundPanel area = new GlassBackgroundPanel();
        area.setLayout(new GridBagLayout());

        // Glass card
        GlassCard glassCard = new GlassCard();
        glassCard.setLayout(new BorderLayout());
        glassCard.setBorder(new EmptyBorder(24, 22, 24, 22));
        glassCard.setPreferredSize(new Dimension(420, 510));

        // Form content
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(20, 18, 20, 18));
        form.setPreferredSize(new Dimension(376, 460));

        JLabel eyebrow = new JLabel("WELCOME BACK");
        eyebrow.setFont(AppTheme.semibold(9));
        eyebrow.setForeground(AppTheme.primary());
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = new JLabel("Sign in to your account");
        heading.setFont(AppTheme.semibold(24));
        heading.setForeground(NAVY);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(
                "Enter your details to continue to EquipLink."
        );
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(new Color(105, 125, 155));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(eyebrow);
        form.add(Box.createVerticalStrut(8));

        form.add(heading);
        form.add(Box.createVerticalStrut(7));

        form.add(subtitle);
        form.add(Box.createVerticalStrut(27));

        // Input fields
        addField(form, "Username or email", username);
        addField(form, "Password", password);

        // Password assistance
        JPanel passwordHelp = new JPanel(new BorderLayout());
        passwordHelp.setOpaque(false);

        JLabel securityHint = new JLabel(
                "Your account is protected."
        );

        securityHint.setFont(AppTheme.regular(9));
        securityHint.setForeground(new Color(125, 140, 163));

        JButton forgotPassword = new JButton("Forgot password?");
        styleLink(forgotPassword);

        forgotPassword.addActionListener(event ->
                new ForgotPasswordDialog(
                        this,
                        authController
                ).setVisible(true)
        );

        passwordHelp.add(securityHint, BorderLayout.WEST);
        passwordHelp.add(forgotPassword, BorderLayout.EAST);

        passwordHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordHelp.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 25)
        );

        form.add(passwordHelp);
        form.add(Box.createVerticalStrut(22));

        // Login button
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 46)
        );
        loginButton.setPreferredSize(new Dimension(330, 46));

        loginButton.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        );

        loginButton.addActionListener(event -> doLogin());

        form.add(loginButton);
        form.add(Box.createVerticalStrut(18));

        // Registration
        JPanel registerRow = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 4, 0)
        );

        registerRow.setOpaque(false);

        JLabel prompt = new JLabel("New to EquipLink?");
        prompt.setFont(AppTheme.regular(10));
        prompt.setForeground(new Color(115, 130, 151));

        JButton register = new JButton(
                "Create a borrower account"
        );

        styleLink(register);
        register.setFont(AppTheme.semibold(10));

        register.addActionListener(event ->
                new RegisterFrame(
                        this,
                        authController
                ).setVisible(true)
        );

        registerRow.add(prompt);
        registerRow.add(register);

        registerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(registerRow);

        glassCard.add(form, BorderLayout.CENTER);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.CENTER;

        area.add(glassCard, constraints);

        return area;
    }

    // =========================================
    // GLASS INPUT FIELDS
    // =========================================

    private void addField(
            JPanel panel,
            String label,
            JComponent field) {

        JLabel fieldLabel = new JLabel(label);

        fieldLabel.setFont(AppTheme.semibold(10));
        fieldLabel.setForeground(NAVY);
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

    private void styleGlassInput(JComponent field) {

        field.setFont(AppTheme.regular(11));
        field.setForeground(NAVY);
        field.setBackground(INPUT_BACKGROUND);
        field.setOpaque(true);

        field.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                new Color(190, 207, 230),
                                1
                        ),
                        new EmptyBorder(10, 13, 10, 13)
                )
        );

        field.setFocusable(true);
    }

    // =========================================
    // LOGIN FUNCTION
    // =========================================

    private void doLogin() {

        char[] pass = password.getPassword();
        String enteredUsername = username.getText().trim();

        if (enteredUsername.isEmpty() || pass.length == 0) {

            Arrays.fill(pass, '\0');

            JOptionPane.showMessageDialog(
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

                        JOptionPane.showMessageDialog(
                                LoginFrame.this,
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

                    JOptionPane.showMessageDialog(
                            LoginFrame.this,
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

    private void styleLink(JButton button) {

        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);

        button.setForeground(AppTheme.primary());
        button.setFocusPainted(false);

        button.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        );

        button.setFont(AppTheme.regular(9));
    }

    // =========================================
    // GLASS BACKGROUND
    // =========================================

    private static class GlassBackgroundPanel extends JPanel {

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

            // Soft gradient background
            GradientPaint gradient = new GradientPaint(
                    0, 0,
                    new Color(239, 246, 255),
                    width, height,
                    new Color(215, 230, 249)
            );

            g2.setPaint(gradient);
            g2.fillRect(0, 0, width, height);

            // Decorative glass circles
            g2.setColor(new Color(255, 255, 255, 75));
            g2.fillOval(width - 180, 40, 220, 220);

            g2.setColor(new Color(90, 145, 210, 28));
            g2.fillOval(20, height - 175, 220, 220);

            g2.setColor(new Color(255, 255, 255, 45));
            g2.fillOval(width / 2, height / 2, 100, 100);

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

            Graphics2D g2 = (Graphics2D) graphics.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int width = getWidth();
            int height = getHeight();

            // Soft shadow
            for (int i = 5; i >= 1; i--) {

                g2.setColor(
                        new Color(30, 55, 90, 7 + i * 3)
                );

                g2.fillRoundRect(
                        4,
                        5 + i,
                        width - 9,
                        height - 10,
                        30,
                        30
                );
            }

            // Translucent glass gradient
            GradientPaint glassGradient = new GradientPaint(
                    0, 0,
                    new Color(255, 255, 255, 235),
                    width, height,
                    new Color(240, 247, 255, 200)
            );

            g2.setPaint(glassGradient);

            g2.fillRoundRect(
                    4,
                    4,
                    width - 9,
                    height - 10,
                    28,
                    28
            );

            // Glass border
            g2.setColor(GLASS_BORDER);

            g2.setStroke(new BasicStroke(1.5f));

            g2.drawRoundRect(
                    4,
                    4,
                    width - 9,
                    height - 10,
                    28,
                    28
            );

            // Subtle highlight
            g2.setColor(
                    new Color(255, 255, 255, 115)
            );

            g2.drawLine(
                    30,
                    15,
                    width - 30,
                    15
            );

            g2.dispose();

            super.paintComponent(graphics);
        }
    }

    // =========================================
    // STANDARD BUTTON STYLE
    // =========================================

    static void styleButton(JButton button) {

        button.setBackground(AppTheme.primary());
        button.setForeground(Color.WHITE);
        button.setFont(AppTheme.semibold(12));

        button.setFocusPainted(false);

        button.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                AppTheme.primary()
                        ),
                        new EmptyBorder(10, 18, 10, 18)
                )
        );

        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );
    }
}
