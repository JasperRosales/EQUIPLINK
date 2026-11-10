package src.ui.components;

import src.ui.AppTheme;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Soft raised (neumorphic) container. Shadows are painted inside its margin. */
public class SurfacePanel extends JPanel {

    /** Space reserved around the body for the soft shadows. */
    public static final int MARGIN = 7;
    private static final int ARC = 18;
    private static final float DEPTH = 3.5f;

    public SurfacePanel() {
        setOpaque(false);
        setBorder(new EmptyBorder(20, 20, 20, 20));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Neu.raised(g, MARGIN, MARGIN, getWidth() - MARGIN * 2, getHeight() - MARGIN * 2,
                ARC, DEPTH, AppTheme.surface());
        g.dispose();
        super.paintComponent(graphics);
    }
}
