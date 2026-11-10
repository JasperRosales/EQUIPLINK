package src.ui.components;

import javax.swing.Timer;
import java.awt.Component;

/**
 * Animates a value between 0 and 1 with ease-out timing and repaints the owner on
 * every frame. Runs on the Swing timer thread (the EDT), so it is safe for painting.
 */
public final class Tween {

    private static final int FRAME_MS = 15;

    private final Component owner;
    private final int durationMs;
    private final Timer timer;
    private float value;
    private float from;
    private float target;
    private long startedAt;
    private Runnable onFrame;

    public Tween(Component owner, int durationMs) {
        this(owner, durationMs, 0f);
    }

    public Tween(Component owner, int durationMs, float initial) {
        this.owner = owner;
        this.durationMs = Math.max(1, durationMs);
        this.value = initial;
        this.target = initial;
        this.timer = new Timer(FRAME_MS, event -> tick());
        this.timer.setCoalesce(true);
    }

    public Tween onFrame(Runnable onFrame) {
        this.onFrame = onFrame;
        return this;
    }

    public float value() {
        return value;
    }

    public float target() {
        return target;
    }

    /** Animate towards {@code next}. */
    public void to(float next) {
        if (next == target && (timer.isRunning() || value == next)) {
            return;
        }
        from = value;
        target = next;
        startedAt = System.currentTimeMillis();
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    /** Jump to a value without animating. */
    public void set(float next) {
        timer.stop();
        value = next;
        target = next;
        frame();
    }

    private void tick() {
        float t = Math.min(1f, (System.currentTimeMillis() - startedAt) / (float) durationMs);
        float eased = 1f - (float) Math.pow(1f - t, 3);
        value = from + (target - from) * eased;
        if (t >= 1f || !owner.isDisplayable()) {
            value = target;
            timer.stop();
        }
        frame();
    }

    private void frame() {
        if (onFrame != null) {
            onFrame.run();
        }
        owner.repaint();
    }
}
