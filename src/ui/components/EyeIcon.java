package src.ui.components;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Vector eye icon for password visibility toggles. Drawn with Java2D so it renders
 * the same on every platform (emoji glyphs are often missing from Swing fonts).
 */
public class EyeIcon implements Icon {

    private final boolean crossed;
    private final Color color;
    private final int size;

    /** @param crossed true for the "hidden" (eye with slash) variant */
    public EyeIcon(boolean crossed, Color color, int size) {
        this.crossed = crossed;
        this.color = color;
        this.size = size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        float s = size;
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.5f, s / 11f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D eye = new Path2D.Float();
        float mid = s / 2f;
        eye.moveTo(s * 0.06f, mid);
        eye.quadTo(mid, s * 0.06f, s * 0.94f, mid);
        eye.quadTo(mid, s * 0.94f, s * 0.06f, mid);
        eye.closePath();
        g.draw(eye);

        float pupil = s * 0.30f;
        g.fill(new java.awt.geom.Ellipse2D.Float(mid - pupil / 2f, mid - pupil / 2f, pupil, pupil));

        if (crossed) {
            g.draw(new java.awt.geom.Line2D.Float(s * 0.14f, s * 0.86f, s * 0.86f, s * 0.14f));
        }
        g.dispose();
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
