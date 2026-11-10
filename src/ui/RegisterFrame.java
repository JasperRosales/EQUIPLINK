package src.ui;

import src.controller.AuthController;
import src.ui.components.AuthBrandPanel;
import src.ui.components.PrimaryButton;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

public class RegisterFrame extends JDialog {

    private final AuthController authController;
    private final JTextField username = new JTextField();
    private final JTextField email = new JTextField();
    private final JTextField fullName = new JTextField();
    private final JTextField department = new JTextField();
    private final JComboBox<String> type = new JComboBox<>(
            new String[] { "STUDENT" });
    private final JPasswordField password = new JPasswordField();
    private final JPasswordField confirmPassword = new JPasswordField();
    private final PrimaryButton createButton = new PrimaryButton("Create account");

    public RegisterFrame(JFrame owner, AuthController authController) {
        super(owner, "Create your EquipLink account", true);
        this.authController = authController;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 650));
        setSize(1020, 720);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.background());
        root.add(new AuthBrandPanel(
                "Make campus resources work for you.",
                "Create a borrower account to request equipment, follow its progress, and see your borrowing history."),
                BorderLayout.WEST);
        root.add(createRegistrationForm(), BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel createRegistrationForm() {
        JPanel area = new JPanel(new GridBagLayout());
        area.setBackground(AppTheme.background());

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(24, 22, 24, 22));
        form.setPreferredSize(new Dimension(460, 620));

        JLabel eyebrow = new JLabel("GET STARTED");
        eyebrow.setFont(AppTheme.semibold(10.5f));
        eyebrow.setForeground(AppTheme.primary());
        eyebrow.setAlignmentX(LEFT_ALIGNMENT);
        JLabel heading = new JLabel("Create your account");
        heading.setFont(AppTheme.semibold(23));
        heading.setForeground(AppTheme.foreground());
        heading.setAlignmentX(LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Student accounts only. Faculty accounts are created by ICT.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        form.add(eyebrow);
        form.add(Box.createVerticalStrut(7));
        form.add(heading);
        form.add(Box.createVerticalStrut(5));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(17));

        addField(form, "Full name", fullName);
        addField(form, "Username", username);
        addField(form, "Email address", email);
        addField(form, "Department / course (optional)", department);
        addField(form, "Password (at least 8 characters)", password);
        addField(form, "Confirm password", confirmPassword);

        createButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        createButton.setAlignmentX(LEFT_ALIGNMENT);
        createButton.addActionListener(event -> createAccount());
        form.add(Box.createVerticalStrut(4));
        form.add(createButton);
        form.add(Box.createVerticalStrut(12));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        footer.setOpaque(false);
        JLabel note = new JLabel("Already have an account?");
        note.setFont(AppTheme.regular(10));
        note.setForeground(AppTheme.muted());
        JLabel close = new JLabel("Close this window to sign in.");
        close.setFont(AppTheme.semibold(10));
        close.setForeground(AppTheme.primary());
        footer.add(note);
        footer.add(close);
        footer.setAlignmentX(LEFT_ALIGNMENT);
        form.add(footer);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppTheme.background());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(12, 12, 12, 12);
        area.add(scroll, constraints);
        return area;
    }

    private void addField(JPanel panel, String label, javax.swing.JComponent input) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(AppTheme.semibold(10));
        fieldLabel.setForeground(AppTheme.foreground());
        fieldLabel.setAlignmentX(LEFT_ALIGNMENT);
        UiSupport.styleInput(input);
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 41));
        input.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(fieldLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(input);
        panel.add(Box.createVerticalStrut(10));
    }

    private void createAccount() {
        char[] pass = password.getPassword();
        char[] confirmation = confirmPassword.getPassword();
        if (!Arrays.equals(pass, confirmation)) {
            clear(pass, confirmation);
            confirmPassword.setText("");
            JOptionPane.showMessageDialog(
                    this,
                    "The passwords do not match.",
                    "Check your password",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        createButton.setEnabled(false);
        createButton.setText("Creating account…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                authController.registerBorrower(
                        username.getText(),
                        email.getText(),
                        fullName.getText(),
                        department.getText(),
                        (String) type.getSelectedItem(),
                        pass);
                return null;
            }

            @Override
            protected void done() {
                clear(pass, confirmation);
                password.setText("");
                confirmPassword.setText("");
                createButton.setEnabled(true);
                createButton.setText("Create account");
                try {
                    get();
                    JOptionPane.showMessageDialog(
                            RegisterFrame.this,
                            "Your account has been created. You can now sign in.",
                            "Registration successful",
                            JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null
                            ? exception : exception.getCause();
                    JOptionPane.showMessageDialog(
                            RegisterFrame.this,
                            cause.getMessage(),
                            "Registration error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void clear(char[]... secrets) {
        for (char[] secret : secrets) {
            Arrays.fill(secret, '\0');
        }
    }
}
