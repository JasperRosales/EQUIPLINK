package src.ui;

import src.ui.components.LogoAnimationPanel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** Startup splash: plays the logo animation once, then reports it is done. */
public final class SplashWindow extends JWindow {

    private final JLabel status = new JLabel("Starting EquipLink...", SwingConstants.CENTER);

    public SplashWindow(Runnable onAnimationDone) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.LIGHT_BG);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.LIGHT_BORDER),
                BorderFactory.createEmptyBorder(24, 24, 18, 24)));

        LogoAnimationPanel logo = LogoAnimationPanel.intro(384, 256);
        // Hold the final frame briefly before moving on.
        logo.setOnFinished(() -> {
            Timer hold = new Timer(450, e -> onAnimationDone.run());
            hold.setRepeats(false);
            hold.start();
        });
        root.add(logo, BorderLayout.CENTER);

        status.setFont(AppTheme.regular(11));
        status.setForeground(AppTheme.INK_MUTED);
        root.add(status, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(432, 330));
        setLocationRelativeTo(null);
        javax.swing.ImageIcon icon = BrandLogo.icon(64);
        if (icon != null) {
            setIconImage(icon.getImage());
        }
    }

    public void setStatus(String text) {
        status.setText(text);
    }
}
