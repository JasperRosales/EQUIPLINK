
package src.ui;

import src.controller.AuthController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Soft neumorphic control used by ForgotPasswordDialog. */
class SoftPasswordField extends JPasswordField {

    SoftPasswordField() {

        setFont(AppTheme.regular(11));
        setForeground(AppTheme.INK);
        setCaretColor(AppTheme.PRIMARY);
        setOpaque(false);

        setBorder(
                new EmptyBorder(12, 15, 12, 15)
        );
    }

    @Override
    protected void paintComponent(Graphics graphics) {

        Graphics2D g2 =
                (Graphics2D) graphics.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        int width = getWidth();
        int height = getHeight();

        GradientPaint gradient = new GradientPaint(
                0, 0,
                new Color(255, 255, 255),
                0, height,
                new Color(255, 249, 242)
        );

        g2.setPaint(gradient);

        g2.fillRoundRect(
                1, 1,
                width - 3,
                height - 3,
                14, 14
        );

        g2.setColor(AppTheme.LIGHT_BORDER);

        g2.drawRoundRect(
                1, 1,
                width - 3,
                height - 3,
                14, 14
        );

        g2.setColor(
                new Color(255, 255, 255, 210)
        );

        g2.drawLine(
                15, 3,
                width - 16, 3
        );

        g2.setColor(
                new Color(168, 120, 128, 85)
        );

        g2.drawLine(
                15, height - 3,
                width - 16, height - 3
        );

        if (hasFocus()) {

            g2.setColor(
                    AppTheme.alpha(AppTheme.PRIMARY, 150)
            );

            g2.setStroke(new BasicStroke(1.5f));

            g2.drawRoundRect(
                    1, 1,
                    width - 3,
                    height - 3,
                    14, 14
            );
        }

        g2.dispose();

        super.paintComponent(graphics);
    }
}
