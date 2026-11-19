
package src.ui;

import src.controller.AuthController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Part 3 of ForgotPasswordDialog, split out to keep files short. */
public abstract class ForgotPasswordDialogPart3 extends ForgotPasswordDialogPart2 {

    protected ForgotPasswordDialogPart3(java.awt.Window owner, String title, java.awt.Dialog.ModalityType modality) {
        super(owner, title, modality);
    }


    // =========================================
    // MAIN CONTENT
    // =========================================

    JPanel buildContent() {

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
                new Dimension(550, 620)
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
                "Verify your registered email, then submit a request for "
                + "authorized review. Elevated accounts require a Facility "
                + "Admin or Campus Director reviewer. After approval, return "
                + "here with the same identity and email to set your new password."
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
                "Registered email (identity verification)",
                registeredEmail
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
    // REQUEST ICT APPROVAL
    // =========================================

    void requestApproval() {

        String enteredIdentity =
                identity.getText().trim();
        String enteredEmail =
                registeredEmail.getText().trim();

        if (enteredIdentity.isEmpty() || enteredEmail.isEmpty()) {

            showMessage(
                    "Enter your username or email and registered email.",
                    "Missing information"
            );

            return;
        }

        setBusy(true);

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {

                authController.requestPasswordReset(
                        enteredIdentity,
                        enteredEmail
                );

                return null;
            }

            @Override
            protected void done() {

                setBusy(false);

                try {

                    get();

                    showMessage(
                            "Identity verified. The reset request is now waiting "
                                    + "for authorized approval.",
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

    void resetPassword() {

        String enteredIdentity =
                identity.getText().trim();
        String enteredEmail =
                registeredEmail.getText().trim();

        char[] password = newPassword.getPassword();
        char[] confirmation = confirmPassword.getPassword();

        if (enteredIdentity.isEmpty() || enteredEmail.isEmpty()) {

            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');

            showMessage(
                    "Enter your username or email and registered email.",
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
                        enteredEmail,
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
}
