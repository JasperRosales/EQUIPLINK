
package src.ui;

import src.controller.AuthController;
import src.ui.components.PrimaryButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

public class PasswordChangeDialog extends PasswordChangeDialogPart2 {

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

    void buildContent() {

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

        setContentPane(src.ui.components.Motion.popIn(root));
    }
}
