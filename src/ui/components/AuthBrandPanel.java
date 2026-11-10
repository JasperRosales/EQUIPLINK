package src.ui.components;

import src.ui.AppTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class AuthBrandPanel extends JPanel {

    public AuthBrandPanel(String title, String subtitle) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(40, 38, 38, 34));
        setOpaque(false);

        JPanel logo = new JPanel();
        logo.setOpaque(false);
        logo.setLayout(new BoxLayout(logo, BoxLayout.X_AXIS));
        JLabel badge = new JLabel("E");
        badge.setOpaque(true);
        badge.setBackground(new Color(255, 255, 255, 32));
        badge.setForeground(Color.WHITE);
        badge.setFont(AppTheme.semibold(19));
        badge.setBorder(new EmptyBorder(7, 14, 7, 14));
        JLabel brand = new JLabel("EquipLink");
        brand.setFont(AppTheme.semibold(18));
        brand.setForeground(Color.WHITE);
        logo.add(badge);
        logo.add(Box.createHorizontalStrut(12));
        logo.add(brand);
        logo.setAlignmentX(LEFT_ALIGNMENT);
        add(logo);

        add(Box.createVerticalGlue());
        JLabel eyebrow = new JLabel("CAMPUS EQUIPMENT, SIMPLIFIED");
        eyebrow.setFont(AppTheme.semibold(9));
        eyebrow.setForeground(new Color(193, 214, 255));
        eyebrow.setAlignmentX(LEFT_ALIGNMENT);
        add(eyebrow);
        add(Box.createVerticalStrut(14));

        JLabel heading = new JLabel("<html><div style='width:310px'>" + title + "</div></html>");
        heading.setFont(AppTheme.semibold(29));
        heading.setForeground(Color.WHITE);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        add(heading);
        add(Box.createVerticalStrut(12));

        JLabel description = new JLabel(
                "<html><div style='width:300px;line-height:1.55'>" + subtitle + "</div></html>");
        description.setFont(AppTheme.regular(12));
        description.setForeground(new Color(213, 223, 239));
        description.setAlignmentX(LEFT_ALIGNMENT);
        add(description);
        add(Box.createVerticalGlue());

        JLabel footer = new JLabel("One campus. Shared resources. Easier borrowing.");
        footer.setFont(AppTheme.regular(9));
        footer.setForeground(new Color(177, 195, 220));
        footer.setAlignmentX(LEFT_ALIGNMENT);
        add(footer);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(
                0, 0, new Color(28, 57, 107),
                getWidth(), getHeight(), new Color(47, 105, 190)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(255, 255, 255, 13));
        g.fillOval(getWidth() - 150, 35, 205, 205);
        g.fillOval(getWidth() - 92, 95, 90, 90);
        g.fillOval(-90, getHeight() - 180, 225, 225);
        g.dispose();
        super.paintComponent(graphics);
    }
}
