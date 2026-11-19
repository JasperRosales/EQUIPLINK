package src.ui;

import src.controller.AuthController;
import src.model.User;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

public class UserManagementPanel extends JPanel {

    private final User actor;
    private final AuthController authController;
    private final JComboBox<String> borrowerType = new JComboBox<>(
            new String[] { "All users", "Faculty", "Student" });
    private final JComboBox<String> department = new JComboBox<>();
    private final JTextField search = new JTextField(20);
    private final JPanel usersList = new JPanel();
    private List<User> users = List.of();

    public UserManagementPanel(User actor, AuthController authController) {
        this.actor = actor;
        this.authController = authController;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        addHeader();
        usersList.setOpaque(false);
        usersList.setLayout(new BoxLayout(usersList, BoxLayout.Y_AXIS));
        add(usersList);
        loadUsers();
    }

    private void addHeader() {
        JLabel title = new JLabel("Borrower accounts");
        title.setFont(AppTheme.semibold(18));
        title.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel(
                "Students self-register. Faculty accounts are created here and get priority handling.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        JPanel titleBar = new JPanel(new BorderLayout(12, 0));
        titleBar.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);
        titleBar.add(titles, BorderLayout.CENTER);
        src.ui.components.PrimaryButton createFaculty =
                new src.ui.components.PrimaryButton("Create faculty account");
        createFaculty.addActionListener(event -> createFacultyAccount(createFaculty));
        JPanel actionWrap = new JPanel(new java.awt.GridBagLayout());
        actionWrap.setOpaque(false);
        actionWrap.add(createFaculty);
        titleBar.add(actionWrap, BorderLayout.EAST);
        add(titleBar);
        add(Box.createVerticalStrut(12));

        JPanel filters = new JPanel(new BorderLayout(8, 0));
        filters.setOpaque(false);
        borrowerType.setFont(AppTheme.regular(11));
        borrowerType.addActionListener(event -> showUsers());
        department.setFont(AppTheme.regular(11));
        department.addActionListener(event -> showUsers());
        JPanel selects = new JPanel(new BorderLayout(8, 0));
        selects.setOpaque(false);
        selects.add(borrowerType, BorderLayout.WEST);
        selects.add(department, BorderLayout.CENTER);
        filters.add(selects, BorderLayout.WEST);
        UiSupport.styleInput(search);
        search.getDocument().addDocumentListener(UiSupport.documentListener(this::showUsers));
        filters.add(search, BorderLayout.CENTER);
        add(filters);
        add(Box.createVerticalStrut(14));
    }

    private void loadUsers() {
        usersList.removeAll();
        usersList.add(message("Loading borrower accounts…", false));
        new SwingWorker<List<User>, Void>() {
            @Override
            protected List<User> doInBackground() throws Exception {
                return authController.findBorrowersForIct(actor);
            }

            @Override
            protected void done() {
                try {
                    users = get();
                    updateDepartments();
                    showUsers();
                } catch (Exception exception) {
                    usersList.removeAll();
                    usersList.add(message(
                            "Unable to load borrower accounts: " + messageOf(exception), true));
                    usersList.revalidate();
                    usersList.repaint();
                }
            }
        }.execute();
    }

    private void updateDepartments() {
        String selected = (String) department.getSelectedItem();
        Set<String> departments = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        departments.add("All departments");
        for (User user : users) {
            if (user.getDepartment() != null && !user.getDepartment().isBlank()) {
                departments.add(user.getDepartment().trim());
            }
        }
        department.removeAllItems();
        for (String value : departments) {
            department.addItem(value);
        }
        if (selected != null) {
            department.setSelectedItem(departments.contains(selected)
                    ? selected : "All departments");
        }
    }

    private void showUsers() {
        usersList.removeAll();
        String selectedType = (String) borrowerType.getSelectedItem();
        String selectedDepartment = (String) department.getSelectedItem();
        String query = search.getText().trim().toLowerCase();
        List<User> filtered = new ArrayList<>();
        for (User user : users) {
            if ("Faculty".equals(selectedType)
                    && !"FACULTY".equals(user.getBorrowerType())) {
                continue;
            }
            if ("Student".equals(selectedType)
                    && !"STUDENT".equals(user.getBorrowerType())) {
                continue;
            }
            if (selectedDepartment != null
                    && !"All departments".equals(selectedDepartment)
                    && !selectedDepartment.equalsIgnoreCase(
                            user.getDepartment() == null ? "" : user.getDepartment().trim())) {
                continue;
            }
            String searchable = user.getFullName() + " " + user.getUsername()
                    + " " + user.getEmail() + " " + user.getDepartment();
            if (searchable.toLowerCase().contains(query)) {
                filtered.add(user);
            }
        }
        if (filtered.isEmpty()) {
            usersList.add(message("No accounts match the selected filters.", false));
        } else {
            for (User user : filtered) {
                usersList.add(userCard(user));
                usersList.add(Box.createVerticalStrut(8));
            }
        }
        usersList.revalidate();
        usersList.repaint();
    }

    private JPanel userCard(User user) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout(12, 4));
        JLabel name = new JLabel(user.getFullName());
        name.setFont(AppTheme.semibold(12));
        name.setForeground(AppTheme.foreground());
        String info = user.getUsername() + "  ·  " + user.getEmail() + "  ·  "
                + user.getBorrowerType()
                + (user.getDepartment() == null ? "" : "  ·  " + user.getDepartment());
        JLabel meta = new JLabel(info);
        meta.setFont(AppTheme.regular(10));
        meta.setForeground(AppTheme.muted());
        JPanel detail = new JPanel();
        detail.setOpaque(false);
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.add(name);
        detail.add(Box.createVerticalStrut(3));
        detail.add(meta);
        JLabel status = new JLabel(user.isActive() ? "ACTIVE" : "INACTIVE");
        status.setFont(AppTheme.semibold(10.5f));
        status.setForeground(user.isActive() ? AppTheme.success() : AppTheme.danger());
        src.ui.components.PrimaryButton delete =
                new src.ui.components.PrimaryButton(
                        "Delete", src.ui.components.PrimaryButton.Style.DANGER);
        delete.addActionListener(event -> deleteBorrowerAccount(user, delete));
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        status.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);
        delete.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);
        actions.add(status);
        actions.add(Box.createVerticalStrut(6));
        actions.add(delete);
        card.add(detail, BorderLayout.CENTER);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private void deleteBorrowerAccount(
            User user, src.ui.components.PrimaryButton deleteButton) {
        if (!UiSupport.beginAction(deleteButton)) {
            return;
        }
        int answer = UiSupport.showConfirmDialog(
                this,
                "Permanently delete " + user.getFullName() + " ("
                        + user.getUsername() + ")?\n\n"
                        + "Existing requests and related history are preserved, so an account "
                        + "with linked records cannot be deleted.",
                "Delete borrower account",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.YES_OPTION) {
            UiSupport.endAction(deleteButton);
            return;
        }

        deleteButton.setText("Deleting...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                authController.deleteBorrowerAccount(actor, user.getUserId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadUsers();
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(
                            UserManagementPanel.this,
                            messageOf(exception),
                            "Unable to delete account",
                            javax.swing.JOptionPane.WARNING_MESSAGE);
                } finally {
                    deleteButton.setText("Delete");
                    UiSupport.endAction(deleteButton);
                }
            }
        }.execute();
    }

    private void createFacultyAccount(javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        try {
            JTextField fullName = new JTextField(24);
        JTextField username = new JTextField(24);
        JTextField email = new JTextField(24);
        JTextField departmentField = new JTextField(24);
        JTextField password = new JTextField(generatePassword(), 24);
        password.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
        src.ui.components.PrimaryButton generate = new src.ui.components.PrimaryButton(
                "Generate", src.ui.components.PrimaryButton.Style.SECONDARY);
        generate.addActionListener(event -> password.setText(generatePassword()));

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets = new java.awt.Insets(5, 4, 5, 4);
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        Object[][] rows = {
                { "Full name", fullName }, { "Username", username },
                { "Email address", email }, { "Department", departmentField },
                { "Temporary password", password } };
        for (int i = 0; i < rows.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            c.weightx = 0;
            JLabel label = new JLabel((String) rows[i][0]);
            label.setFont(AppTheme.semibold(12));
            form.add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            form.add((java.awt.Component) rows[i][1], c);
        }
        c.gridx = 2;
        c.weightx = 0;
        form.add(generate, c);
        c.gridy = rows.length;
        c.gridx = 0;
        c.gridwidth = 3;
        JLabel note = new JLabel(
                "Share the temporary password with the faculty member; they can change it in Settings.");
        note.setFont(AppTheme.regular(11));
        note.setForeground(AppTheme.muted());
        form.add(note, c);

        while (true) {
            int answer = UiSupport.showConfirmDialog(
                    this, form, "Create faculty account",
                    javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (answer != javax.swing.JOptionPane.OK_OPTION) {
                return;
            }
            String temporary = password.getText().trim();
            try {
                authController.createFacultyAccount(
                        actor, username.getText(), email.getText(), fullName.getText(),
                        departmentField.getText(), temporary.toCharArray());
            } catch (Exception exception) {
                UiSupport.showMessageDialog(this,
                        messageOf(exception), "Unable to create account",
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                continue;
            }
            JTextField shown = new JTextField(temporary);
            shown.setEditable(false);
            JPanel done = new JPanel(new BorderLayout(0, 8));
            done.add(new JLabel("Faculty account created for " + fullName.getText().trim()
                    + " (" + username.getText().trim() + ")."), BorderLayout.NORTH);
            done.add(shown, BorderLayout.CENTER);
            done.add(new JLabel("Temporary password, copy it now; it won't be shown again."),
                    BorderLayout.SOUTH);
            UiSupport.showMessageDialog(this, done, "Account created",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            borrowerType.setSelectedItem("Faculty");
            loadUsers();
            return;
        }
        } finally {
            UiSupport.endAction(source);
        }
    }

    private static String generatePassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            value.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return value.toString();
    }

    private JPanel message(String text, boolean error) {
        SurfacePanel panel = new SurfacePanel();
        panel.setLayout(new BorderLayout());
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.regular(11));
        label.setForeground(error ? AppTheme.danger() : AppTheme.muted());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private String messageOf(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause() : exception;
        return cause.getMessage();
    }
}
