package src.ui.components;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Wraps page content and fades/slides it in when first shown. */
public class FadeInPanel extends JPanel {

    private static final int SLIDE_PX = 14;
    private final Tween progress;

    public FadeInPanel(JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        add(content, BorderLayout.CENTER);
        progress = new Tween(this, 260, 0f);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        progress.set(0f);
        progress.to(1f);
    }

    @Override
    public void paint(Graphics graphics) {
        float p = progress.value();
        if (p >= 1f) {
            super.paint(graphics);
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, p)));
        g.translate(0, Math.round((1f - p) * SLIDE_PX));
        super.paint(g);
        g.dispose();
    }
}
