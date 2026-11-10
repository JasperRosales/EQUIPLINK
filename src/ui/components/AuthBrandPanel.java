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
        javax.swing.ImageIcon mark = src.ui.BrandLogo.icon(40);
        JLabel badge;
        if (mark != null) {
            badge = new JLabel(mark);
        } else {
            badge = new Avatar("EL", AppTheme.primary(), 10);
            badge.setFont(AppTheme.semibold(13));
            badge.setPreferredSize(new java.awt.Dimension(38, 38));
            badge.setMaximumSize(new java.awt.Dimension(38, 38));
        }
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
        eyebrow.setFont(AppTheme.semibold(10.5f));
        eyebrow.setForeground(new Color(147, 197, 253));
        eyebrow.setAlignmentX(LEFT_ALIGNMENT);
        add(eyebrow);
        add(Box.createVerticalStrut(14));

        JLabel heading = new JLabel("<html><div style='width:310px'>" + title + "</div></html>");
        heading.setFont(AppTheme.semibold(28));
        heading.setForeground(Color.WHITE);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        add(heading);
        add(Box.createVerticalStrut(12));

        JLabel description = new JLabel(
                "<html><div style='width:300px;line-height:1.55'>" + subtitle + "</div></html>");
        description.setFont(AppTheme.regular(13));
        description.setForeground(new Color(203, 213, 225));
        description.setAlignmentX(LEFT_ALIGNMENT);
        add(description);
        add(Box.createVerticalGlue());

        JLabel footer = new JLabel("One campus. Shared resources. Easier borrowing.");
        footer.setFont(AppTheme.regular(11));
        footer.setForeground(new Color(148, 163, 184));
        footer.setAlignmentX(LEFT_ALIGNMENT);
        add(footer);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(
                0, 0, new Color(15, 23, 42),
                0, getHeight(), new Color(23, 37, 84)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(255, 255, 255, 8));
        for (int x = 24; x < getWidth(); x += 24) {
            for (int y = 24; y < getHeight(); y += 24) {
                g.fillOval(x, y, 2, 2);
            }
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
