
package src;

import src.database.DatabaseInitializer;
import src.database.DatabaseSeeder;
import src.controller.AuthController;
import src.controller.BorrowRequestController;
import src.controller.EquipmentController;
import src.controller.NotificationController;
import src.layout.MainLayout;
import src.model.User;
import src.service.AuthService;
import src.service.BorrowRequestService;
import src.service.EquipmentService;
import src.service.NotificationService;
import src.ui.LoginFrame;

import javax.swing.*;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(src.ui.AppTheme::install);

        try {

            DatabaseInitializer.initializeDatabase();

            DatabaseSeeder.seedAccounts();

            System.out.println("EquipLink is ready.");

            SwingUtilities.invokeLater(() -> {

                AuthService authService = new AuthService();

                AuthController authController = new AuthController(
                        authService);
                BorrowRequestController requestController =
                        new BorrowRequestController(new BorrowRequestService());
                EquipmentController equipmentController =
                        new EquipmentController(new EquipmentService());
                NotificationController notificationController =
                        new NotificationController(new NotificationService());

                showLogin(authController, requestController, equipmentController,
                        notificationController);
            });

        } catch (SQLException exception) {

            exception.printStackTrace();

            JOptionPane.showMessageDialog(
                    null,
                    "Unable to initialize EquipLink database.\n"
                            + exception.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void showLogin(
            AuthController authController,
            BorrowRequestController requestController,
            EquipmentController equipmentController,
            NotificationController notificationController) {
        LoginFrame loginFrame = new LoginFrame(
                authController,
                user -> showDashboard(
                        user, authController, requestController,
                        equipmentController, notificationController));
        loginFrame.setVisible(true);
    }

    private static void showDashboard(
            User user,
            AuthController authController,
            BorrowRequestController requestController,
            EquipmentController equipmentController,
            NotificationController notificationController) {
        MainLayout dashboard = new MainLayout(
                user, authController, requestController,
                equipmentController, notificationController);
        JFrame frame = new JFrame("EquipLink | " + user.getRole());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new java.awt.Dimension(960, 640));
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null);
        frame.setContentPane(dashboard);
        dashboard.setLogoutAction(() -> {
            frame.dispose();
            showLogin(authController, requestController,
                    equipmentController, notificationController);
        });
        frame.setVisible(true);
    }
}
