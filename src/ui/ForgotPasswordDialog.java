
package src.ui;

import src.controller.AuthController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class ForgotPasswordDialog extends ForgotPasswordDialogPart3 {

    // =========================================
    // CONSTRUCTOR
    // =========================================

    public ForgotPasswordDialog(
            Window owner,
            AuthController authController) {

        super(owner, "Forgot password", ModalityType.APPLICATION_MODAL);

        this.authController = authController;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(680, 720));
        setSize(700, 740);
        setResizable(false);

        setContentPane(src.ui.components.Motion.popIn(buildContent()));
        setLocationRelativeTo(owner);
    }
}
