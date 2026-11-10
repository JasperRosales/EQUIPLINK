package src.ui;

import src.controller.AuthController;
import src.model.PasswordResetRequest;
import src.model.User;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PasswordResetRequestsPanel extends JPanel {

    private final User actor;
    private final AuthController authController;
    private final JTextField search = new JTextField(22);
    private final JPanel requestsList = new JPanel();
    private List<PasswordResetRequest> requests = List.of();

    public PasswordResetRequestsPanel(User actor, AuthController authController) {
        this.actor = actor;
        this.authController = authController;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppTheme.background());
        setBorder(new EmptyBorder(2, 2, 22, 2));
        addHeader();
        requestsList.setOpaque(false);
        requestsList.setLayout(new BoxLayout(requestsList, BoxLayout.Y_AXIS));
        add(requestsList);
        loadRequests();
    }

    private void addHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Password change requests");
        title.setFont(AppTheme.semibold(18));
        title.setForeground(AppTheme.foreground());
        JLabel subtitle = new JLabel("Review and approve password recovery requests from all account roles.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        header.add(text, BorderLayout.NORTH);

        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.setOpaque(false);
        UiSupport.styleInput(search);
        search.addActionListener(event -> showRequests());
        search.getDocument().addDocumentListener(UiSupport.documentListener(this::showRequests));
        controls.add(search, BorderLayout.CENTER);
        PrimaryButton refresh = new PrimaryButton("Refresh", PrimaryButton.Style.SECONDARY);
        refresh.addActionListener(event -> loadRequests());
        controls.add(refresh, BorderLayout.EAST);
        header.add(controls, BorderLayout.CENTER);
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(14));
    }

    private void loadRequests() {
        showMessage("Loading password reset requests…", false);
        new SwingWorker<List<PasswordResetRequest>, Void>() {
            @Override
            protected List<PasswordResetRequest> doInBackground() throws Exception {
                return authController.findPasswordResetRequests(actor);
            }

            @Override
            protected void done() {
                try {
                    requests = get();
                    showRequests();
                } catch (Exception exception) {
                    showMessage("Unable to load requests: " + messageOf(exception), true);
                }
            }
        }.execute();
    }

    private void showRequests() {
        requestsList.removeAll();
        String query = search.getText().trim().toLowerCase();
        List<PasswordResetRequest> filtered = new ArrayList<>();
        for (PasswordResetRequest request : requests) {
            String searchable = request.getFullName() + " " + request.getUsername()
                    + " " + request.getEmail() + " " + request.getRole()
                    + " " + request.getDepartment();
            if (searchable.toLowerCase().contains(query)) {
                filtered.add(request);
            }
        }
        if (filtered.isEmpty()) {
            requestsList.add(message(
                    requests.isEmpty()
                            ? "There are no password reset requests waiting for review."
                            : "No requests match your search.",
                    false));
        } else {
            for (PasswordResetRequest request : filtered) {
                requestsList.add(requestCard(request));
                requestsList.add(Box.createVerticalStrut(9));
            }
        }
        requestsList.revalidate();
        requestsList.repaint();
    }

    private JPanel requestCard(PasswordResetRequest request) {
        SurfacePanel card = new SurfacePanel();
        card.setLayout(new BorderLayout(12, 8));
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(request.getFullName() + "  ·  " + request.getUsername());
        name.setFont(AppTheme.semibold(13));
        name.setForeground(AppTheme.foreground());
        String department = request.getDepartment() == null
                ? "No department" : request.getDepartment();
        JLabel meta = new JLabel(request.getEmail() + "  ·  "
                + request.getRole().replace('_', ' ') + "  ·  " + department);
        meta.setFont(AppTheme.regular(10));
        meta.setForeground(AppTheme.muted());
        JLabel date = new JLabel("Requested "
                + request.getRequestedAt().format(
                        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")));
        date.setFont(AppTheme.regular(10.5f));
        date.setForeground(AppTheme.muted());
        details.add(name);
        details.add(Box.createVerticalStrut(4));
        details.add(meta);
        details.add(Box.createVerticalStrut(4));
        details.add(date);
        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        PrimaryButton approve = new PrimaryButton("Approve");
        approve.addActionListener(event -> review(request, true));
        PrimaryButton reject = new PrimaryButton("Reject", PrimaryButton.Style.DANGER);
        reject.addActionListener(event -> review(request, false));
        actions.add(approve);
        actions.add(reject);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void review(PasswordResetRequest request, boolean approve) {
        String note = JOptionPane.showInputDialog(
                this,
                approve ? "Optional approval note:" : "Reason for declining this request:",
                approve ? "Approve password reset" : "Decline password reset",
                JOptionPane.QUESTION_MESSAGE);
        if (!approve && note == null) {
            return;
        }
        if (approve && note == null) {
            note = "";
        }
        if (!approve && note.isBlank()) {
            JOptionPane.showMessageDialog(
                    this, "Enter a reason before declining.", "Reason required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String reviewNote = note;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                authController.reviewPasswordReset(
                        actor, request.getRequestId(), approve, reviewNote);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadRequests();
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(
                            PasswordResetRequestsPanel.this,
                            "Unable to review request: " + messageOf(exception),
                            "Review failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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

    private void showMessage(String text, boolean error) {
        requestsList.removeAll();
        requestsList.add(message(text, error));
        requestsList.revalidate();
        requestsList.repaint();
    }

    private String messageOf(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause() : exception;
        return cause.getMessage();
    }
}
