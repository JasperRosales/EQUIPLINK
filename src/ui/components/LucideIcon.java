package src.ui.components;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/**
 * Lucide-style line icons for EquipLink navigation.
 *
 * <p>The project deliberately ships no SVG/icon dependency: these are 24×24 Lucide
 * paths drawn with Java2D, the same approach already used by {@code EyeIcon} and
 * {@code DateFilter.ChevronIcon}. Colour follows the button foreground so the
 * active/inactive and light/dark states stay consistent automatically.</p>
 */
public final class LucideIcon implements Icon {

    public enum Kind {
        DASHBOARD, EQUIPMENT, REQUEST_EQUIPMENT, MY_REQUESTS, BORROWING,
        NOTIFICATIONS, SETTINGS, INVENTORY, REQUESTS, SCHEDULE, HISTORY,
        PASSWORD, USERS, VENUE, BORROWING_REQUESTS, APPROVAL, ALL_REQUESTS
    }

    private static final Map<String, Kind> MENU_ICONS = Map.ofEntries(
            Map.entry("Dashboard", Kind.DASHBOARD),
            Map.entry("Available Equipment", Kind.EQUIPMENT),
            Map.entry("Request Equipment", Kind.REQUEST_EQUIPMENT),
            Map.entry("My Requests", Kind.MY_REQUESTS),
            Map.entry("Borrowing Request", Kind.BORROWING),
            Map.entry("Notifications", Kind.NOTIFICATIONS),
            Map.entry("Settings", Kind.SETTINGS),
            Map.entry("Inventory", Kind.INVENTORY),
            Map.entry("Requests", Kind.REQUESTS),
            Map.entry("Schedule & Assignment", Kind.SCHEDULE),
            Map.entry("Transaction History", Kind.HISTORY),
            Map.entry("Password Requests", Kind.PASSWORD),
            Map.entry("User Management", Kind.USERS),
            Map.entry("Venue Management", Kind.VENUE),
            Map.entry("Borrowing Requests", Kind.BORROWING_REQUESTS),
            Map.entry("Request Approval", Kind.APPROVAL),
            Map.entry("All Requests", Kind.ALL_REQUESTS));

    private static final int VIEWBOX = 24;
    private static final float STROKE = 1.9f;

    private final Kind kind;
    private final int size;
    private Graphics2D g;

    private LucideIcon(Kind kind, int size) {
        this.kind = kind;
        this.size = size;
    }

    /** 16×16 icon matching a sidebar menu label, or a neutral list icon if unknown. */
    public static Icon forMenu(String label) {
        Kind kind = label == null ? null : MENU_ICONS.get(label);
        return new LucideIcon(kind == null ? Kind.ALL_REQUESTS : kind, 16);
    }

    @Override
    public int getIconWidth() { return size; }

    @Override
    public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        copy.setColor(component.getForeground());
        copy.translate(x, y);
        copy.scale(size / (float) VIEWBOX, size / (float) VIEWBOX);
        copy.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g = copy;
        switch (kind) {
            case DASHBOARD -> dashboard();
            case EQUIPMENT -> box(true);
            case REQUEST_EQUIPMENT -> clipboardPlus();
            case MY_REQUESTS -> clipboardList();
            case BORROWING -> history();
            case NOTIFICATIONS -> bell();
            case SETTINGS -> settings();
            case INVENTORY -> box(false);
            case REQUESTS -> inbox();
            case SCHEDULE -> calendarCheck();
            case HISTORY -> history();
            case PASSWORD -> key();
            case USERS -> users();
            case VENUE -> mapPin();
            case BORROWING_REQUESTS -> clipboardCheck();
            case APPROVAL -> checkCircle();
            case ALL_REQUESTS -> list();
        }
        g.dispose();
        g = null;
    }

    private void dashboard() {
        draw(rect(3, 3, 7, 7, 1.5));
        draw(rect(14, 3, 7, 7, 1.5));
        draw(rect(3, 14, 7, 7, 1.5));
        draw(rect(14, 14, 7, 7, 1.5));
    }

    private void box(boolean tag) {
        draw(rect(3, 7, 18, 14, 2));
        draw(line(3, 12, 21, 12));
        draw(line(12, 7, 12, 21));
        draw(polyline(12, 3, 3, 7, 12, 11, 21, 7, 12, 3));
        if (tag) {
            g.fill(new Ellipse2D.Double(10.7, 10.7, 2.6, 2.6));
        }
    }

    private void clipboardPlus() {
        draw(rect(5, 4, 14, 18, 2));
        draw(rect(9, 2, 6, 4, 1));
        draw(line(12, 11, 12, 17));
        draw(line(9, 14, 15, 14));
    }

    private void clipboardList() {
        draw(rect(5, 4, 14, 18, 2));
        draw(rect(9, 2, 6, 4, 1));
        draw(line(9, 11, 15, 11));
        draw(line(9, 15, 15, 15));
        draw(line(9, 19, 13, 19));
    }

    private void clipboardCheck() {
        draw(rect(5, 4, 14, 18, 2));
        draw(rect(9, 2, 6, 4, 1));
        draw(polyline(8.5, 14, 11, 16.5, 15.5, 11));
    }

    private void inbox() {
        draw(polyline(3, 12, 7, 12, 9, 15, 15, 15, 17, 12, 21, 12));
        draw(polyline(21, 12, 21, 18, 19, 21, 5, 21, 3, 18, 3, 12));
    }

    private void calendarCheck() {
        draw(rect(3, 5, 18, 16, 2));
        draw(line(3, 10, 21, 10));
        draw(line(8, 3, 8, 7));
        draw(line(16, 3, 16, 7));
        draw(polyline(9, 15.5, 11.5, 18, 16, 13));
    }

    private void history() {
        draw(new Ellipse2D.Double(3, 3, 18, 18));
        draw(polyline(12, 7.5, 12, 12, 15, 14));
        draw(polyline(3.5, 9, 3.5, 4, 8.5, 4));
        draw(line(3.5, 4, 6.5, 7));
    }

    private void bell() {
        draw(polyline(6, 17, 3.5, 17, 6, 13, 6, 9));
        draw(new Arc2D.Double(6, 3, 12, 12, 180, 180, Arc2D.OPEN));
        draw(polyline(18, 9, 18, 13, 20.5, 17, 6, 17));
        draw(polyline(10, 20.5, 14, 20.5));
    }

    private void settings() {
        draw(new Ellipse2D.Double(8.5, 8.5, 7, 7));
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            draw(line(12 + 7.5 * cos, 12 + 7.5 * sin,
                    12 + 10.5 * cos, 12 + 10.5 * sin));
        }
    }

    private void key() {
        draw(new Ellipse2D.Double(4, 11, 8, 8));
        draw(polyline(10, 13, 20, 3));
        draw(polyline(17, 6, 20, 9));
        draw(polyline(14, 9, 17, 12));
    }

    private void users() {
        draw(new Ellipse2D.Double(5, 4, 8, 8));
        draw(polyline(2, 21, 2, 19, 6, 15, 12, 15, 16, 19, 16, 21));
        draw(new Ellipse2D.Double(15, 5, 6, 6));
        draw(polyline(16, 14, 18, 13, 22, 13));
    }

    private void mapPin() {
        draw(new Ellipse2D.Double(4, 2, 16, 16));
        draw(polyline(7, 14, 12, 21, 17, 14));
        draw(new Ellipse2D.Double(9.5, 6.5, 5, 5));
    }

    private void checkCircle() {
        draw(new Ellipse2D.Double(3, 3, 18, 18));
        draw(polyline(8, 12, 11, 15, 16, 9));
    }

    private void list() {
        for (int y : new int[] {6, 12, 18}) {
            draw(line(9, y, 21, y));
            draw(new Ellipse2D.Double(3.5, y - 1.5, 3, 3));
        }
    }

    private void draw(Shape shape) {
        g.draw(shape);
    }

    private static Shape line(double x1, double y1, double x2, double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private static Shape polyline(double... points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) {
            path.lineTo(points[i], points[i + 1]);
        }
        return path;
    }

    private static Shape rect(double x, double y, double width, double height, double arc) {
        return new RoundRectangle2D.Double(x, y, width, height, arc, arc);
    }
}
