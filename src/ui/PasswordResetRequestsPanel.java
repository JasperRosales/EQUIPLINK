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
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PasswordResetRequestsPanel extends JPanel {

    private final User actor;
    private final AuthController authController;
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
        JLabel subtitle = new JLabel("Password recovery requests from all roles. Verified requests are marked; elevated accounts require a Facility Admin or Campus Director reviewer.");
        subtitle.setFont(AppTheme.regular(11));
        subtitle.setForeground(AppTheme.muted());
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        header.add(text, BorderLayout.NORTH);
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
        if (requests.isEmpty()) {
            requestsList.add(message(
                    "There are no password reset requests waiting for review.", false));
        } else {
            for (PasswordResetRequest request : requests) {
                requestsList.add(requestCard(request));
                requestsList.add(Box.createVerticalStrut(9));
            }
        }
        requestsList.revalidate();
        requestsList.repaint();
    }

    private JPanel requestCard(PasswordResetRequest request) {
        final float[] hover = {0f};
        SurfacePanel card = new SurfacePanel() {
            @Override
            protected void paintBorder(java.awt.Graphics graphics) {
                super.paintBorder(graphics);
                if (hover[0] <= 0f) return;
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(AppTheme.alpha(AppTheme.PRIMARY, Math.round(255 * hover[0])));
                g.setStroke(new java.awt.BasicStroke(2f));
                int m = SurfacePanel.MARGIN;
                g.drawRoundRect(m, m, getWidth() - m * 2 - 1, getHeight() - m * 2 - 1, 18, 18);
                g.dispose();
            }
        };
        javax.swing.Timer hoverTimer = new javax.swing.Timer(15, null);
        hoverTimer.addActionListener(event -> {
            boolean inside = card.getMousePosition() != null;
            float target = inside ? 1f : 0f, step = 15f / 200f;
            if (Math.abs(hover[0] - target) <= step) { hover[0] = target; hoverTimer.stop(); }
            else { hover[0] += hover[0] < target ? step : -step; }
            card.repaint();
        });
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { if (!hoverTimer.isRunning()) hoverTimer.start(); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { if (!hoverTimer.isRunning()) hoverTimer.start(); }
        });
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
        boolean verified = request.isRegisteredEmailMatch() && request.isIdentityVerified();
        JLabel verification = new JLabel(verified
                ? "✓ Identity verified via registered email"
                : "! Identity verification incomplete");
        verification.setFont(AppTheme.semibold(10));
        verification.setForeground(verified ? AppTheme.success() : AppTheme.danger());
        details.add(Box.createVerticalStrut(4));
        details.add(verification);
        card.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        PrimaryButton approve = new PrimaryButton("Approve");
        boolean canApprove = canApprove(request);
        approve.setEnabled(canApprove);
        if (!canApprove) {
            approve.setToolTipText("Identity verification or reviewer authorization is incomplete.");
        }
        approve.addActionListener(event -> review(request, true, approve));
        PrimaryButton reject = new PrimaryButton("Reject", PrimaryButton.Style.DANGER);
        reject.addActionListener(event -> review(request, false, reject));
        actions.add(approve);
        actions.add(reject);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private boolean canApprove(PasswordResetRequest request) {
        if (!request.isRegisteredEmailMatch() || !request.isIdentityVerified()) {
            return false;
        }
        if (actor.getUserId() == request.getUserId()) {
            return false;
        }
        String actorRole = actor.getRole() == null ? "" : actor.getRole().trim().toUpperCase();
        String targetRole = request.getRole() == null ? "" : request.getRole().trim().toUpperCase();
        boolean elevatedTarget = targetRole.equals("ICT_STAFF")
                || targetRole.equals("ADMIN") || targetRole.equals("DIRECTOR");
        return !elevatedTarget || actorRole.equals("ADMIN") || actorRole.equals("DIRECTOR");
    }

    private void review(
            PasswordResetRequest request, boolean approve, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        String note = UiSupport.showInputDialog(
                this,
                approve ? "Optional approval note:" : "Reason for declining this request:",
                approve ? "Approve password reset" : "Decline password reset",
                JOptionPane.QUESTION_MESSAGE);
        if (!approve && note == null) {
            UiSupport.endAction(source);
            return;
        }
        if (approve && note == null) {
            note = "";
        }
        if (!approve && note.isBlank()) {
            UiSupport.endAction(source);
            UiSupport.showMessageDialog(
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
                    UiSupport.showMessageDialog(
                            PasswordResetRequestsPanel.this,
                            "Unable to review request: " + messageOf(exception),
                            "Review failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
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
