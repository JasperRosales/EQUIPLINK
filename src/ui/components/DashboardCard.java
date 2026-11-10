package src.ui.components;

import src.ui.AppTheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Neumorphic stat card that lifts on hover and counts up numeric values. */
public class DashboardCard extends JPanel {

    private static final int MARGIN = SurfacePanel.MARGIN;
    private static final int ARC = 20;

    private final Color accent;
    private final JLabel amount;
    private final Tween hover = new Tween(this, 180);
    private final Tween press = new Tween(this, 110);
    private Timer counter;
    private Runnable clickAction;

    public DashboardCard(String title, String value, String caption, Color accent) {
        this.accent = accent;
        setLayout(new BorderLayout(10, 12));
        setOpaque(false);
        setBorder(new EmptyBorder(22, 24, 20, 22));

        JLabel label = new JLabel(title);
        label.setFont(AppTheme.semibold(11.5f));
        label.setForeground(AppTheme.muted());

        amount = new JLabel(value);
        amount.setFont(AppTheme.semibold(26));
        amount.setForeground(AppTheme.foreground());
        JLabel note = new JLabel(caption);
        note.setFont(AppTheme.regular(11));
        note.setForeground(AppTheme.muted());

        JPanel lower = new JPanel(new BorderLayout(0, 3));
        lower.setOpaque(false);
        lower.add(amount, BorderLayout.NORTH);
        lower.add(note, BorderLayout.CENTER);

        add(label, BorderLayout.NORTH);
        add(lower, BorderLayout.CENTER);
        installMouseHandlers(this);
    }

    /** Sets the value; whole numbers animate up from the previous value. */
    public void setValue(String value) {
        Integer next = parse(value);
        Integer previous = parse(amount.getText());
        if (counter != null) {
            counter.stop();
        }
        if (next == null || previous == null || next.equals(previous) || !isShowing()) {
            amount.setText(value);
            return;
        }
        long start = System.currentTimeMillis();
        int from = previous;
        counter = new Timer(16, null);
        counter.addActionListener(event -> {
            float t = Math.min(1f, (System.currentTimeMillis() - start) / 450f);
            float eased = 1f - (float) Math.pow(1f - t, 3);
            amount.setText(String.valueOf(Math.round(from + (next - from) * eased)));
            if (t >= 1f) {
                amount.setText(value);
                ((Timer) event.getSource()).stop();
            }
        });
        counter.start();
    }

    private static Integer parse(String text) {
        try {
            return text == null ? null : Integer.valueOf(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public void setClickAction(Runnable clickAction) {
        this.clickAction = clickAction;
        setCursor(Cursor.getPredefinedCursor(
                clickAction == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
    }

    private void installMouseHandlers(Component component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hover.to(1f);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                // Moving between child labels fires exit/enter pairs; only drop
                // the hover once the pointer has really left the card.
                java.awt.Point p = javax.swing.SwingUtilities.convertPoint(
                        event.getComponent(), event.getPoint(), DashboardCard.this);
                if (!DashboardCard.this.contains(p)) {
                    hover.to(0f);
                    press.to(0f);
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                press.to(1f);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                press.to(0f);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (clickAction != null) {
                    clickAction.run();
                }
            }
        });
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                installMouseHandlers(child);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        float h = hover.value();
        float p = press.value();
        int x = MARGIN;
        int y = MARGIN;
        int w = getWidth() - MARGIN * 2;
        int ht = getHeight() - MARGIN * 2;
        float depth = (3.5f + 1.5f * h) * (1f - p);
        if (p > 0.5f) {
            Neu.inset(g, x, y, w, ht, ARC, 3f * p, AppTheme.surface());
        } else {
            Neu.raised(g, x, y, w, ht, ARC, depth, AppTheme.surface());
        }
        if (h > 0f) {
            g.setColor(AppTheme.alpha(accent, Math.round(14 * h)));
            g.fillRoundRect(x, y, w, ht, ARC, ARC);
        }
        // Accent pill grows on hover.
        int pill = Math.round(28 + 22 * h);
        g.setColor(accent);
        g.fillRoundRect(x + 17, y + 8, pill, 4, 4, 4);
        g.dispose();
        super.paintComponent(graphics);
    }
}
