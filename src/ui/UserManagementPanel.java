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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
                "Monitor faculty and student accounts; filter students by department.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        add(title);
        add(Box.createVerticalStrut(4));
        add(subtitle);
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
        Set<String> departments = new LinkedHashSet<>();
        departments.add("All departments");
        for (User user : users) {
            if ("STUDENT".equals(user.getBorrowerType())
                    && user.getDepartment() != null && !user.getDepartment().isBlank()) {
                departments.add(user.getDepartment());
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
            if ("Student".equals(selectedType)
                    && selectedDepartment != null
                    && !"All departments".equals(selectedDepartment)
                    && !selectedDepartment.equals(user.getDepartment())) {
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
        card.add(detail, BorderLayout.CENTER);
        card.add(status, BorderLayout.EAST);
        return card;
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
