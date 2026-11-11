package src.ui.components;

import src.ui.AppTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Button that filters lists by event date. Opens a popup with quick presets and a
 * month calendar: click one day to filter on it, click a second day to make a range.
 */
public class DateFilter extends PrimaryButton {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final List<Runnable> listeners = new ArrayList<>();
    private final JPopupMenu popup = new JPopupMenu();
    private final JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel hint = new JLabel(" ");

    private LocalDate from;
    private LocalDate to;
    private LocalDate pendingStart;
    private YearMonth shownMonth = YearMonth.now();
    private String label = "All dates";

    public DateFilter() {
        super("All dates", Style.SECONDARY);
        setIcon(new ChevronIcon());
        setHorizontalTextPosition(LEFT);
        setIconTextGap(8);
        setToolTipText("Filter by date");
        buildPopup();
        addActionListener(event -> {
            shownMonth = from == null ? YearMonth.now() : YearMonth.from(from);
            pendingStart = null;
            hint.setText("Click a day, or two days for a range.");
            renderMonth();
            popup.show(this, 0, getHeight() + 4);
        });
        updateText();
    }

    /** True when the date passes the current filter. */
    public boolean matches(LocalDate date) {
        if (from == null && to == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    public boolean isActive() {
        return from != null || to != null;
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void clear() {
        apply(null, null, "All dates");
    }

    private void buildPopup() {
        JPanel content = new JPanel(new BorderLayout(14, 0));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.setBackground(AppTheme.surface());

        JPanel presets = new JPanel(new GridLayout(0, 1, 0, 2));
        presets.setOpaque(false);
        LocalDate today = LocalDate.now();
        addPreset(presets, "All dates", null, null);
        addPreset(presets, "Today", today, today);
        addPreset(presets, "Next 7 days", today, today.plusDays(6));
        addPreset(presets, "This week",
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
        addPreset(presets, "This month",
                today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()));
        addPreset(presets, "Upcoming", today, null);
        addPreset(presets, "Past", null, today.minusDays(1));
        JPanel presetWrap = new JPanel(new BorderLayout());
        presetWrap.setOpaque(false);
        presetWrap.add(presets, BorderLayout.NORTH);
        presetWrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, AppTheme.border()),
                new EmptyBorder(0, 0, 0, 10)));
        content.add(presetWrap, BorderLayout.WEST);

        JPanel calendar = new JPanel(new BorderLayout(0, 8));
        calendar.setOpaque(false);
        JPanel navigation = new JPanel(new BorderLayout());
        navigation.setOpaque(false);
        JButton previous = navButton("‹");
        previous.addActionListener(event -> {
            shownMonth = shownMonth.minusMonths(1);
            renderMonth();
        });
        JButton next = navButton("›");
        next.addActionListener(event -> {
            shownMonth = shownMonth.plusMonths(1);
            renderMonth();
        });
        monthLabel.setFont(AppTheme.semibold(13));
        navigation.add(previous, BorderLayout.WEST);
        navigation.add(monthLabel, BorderLayout.CENTER);
        navigation.add(next, BorderLayout.EAST);
        calendar.add(navigation, BorderLayout.NORTH);
        grid.setOpaque(false);
        calendar.add(grid, BorderLayout.CENTER);
        hint.setFont(AppTheme.regular(11));
        hint.setForeground(AppTheme.muted());
        calendar.add(hint, BorderLayout.SOUTH);
        content.add(calendar, BorderLayout.CENTER);

        popup.setBorder(BorderFactory.createLineBorder(AppTheme.border()));
        popup.add(content);
    }

    private void addPreset(JPanel panel, String name, LocalDate start, LocalDate end) {
        JButton button = new JButton(name);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(AppTheme.regular(12.5f));
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(130, 30));
        button.addActionListener(event -> {
            apply(start, end, name);
            popup.setVisible(false);
        });
        panel.add(button);
    }

    private JButton navButton(String text) {
        JButton button = new JButton(text);
        button.setFont(AppTheme.semibold(15));
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void renderMonth() {
        grid.removeAll();
        monthLabel.setText(shownMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                + " " + shownMonth.getYear());
        DayOfWeek day = DayOfWeek.MONDAY;
        for (int i = 0; i < 7; i++) {
            JLabel header = new JLabel(
                    day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).substring(0, 2),
                    SwingConstants.CENTER);
            header.setFont(AppTheme.semibold(10.5f));
            header.setForeground(AppTheme.muted());
            grid.add(header);
            day = day.plus(1);
        }
        int offset = shownMonth.atDay(1).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < offset; i++) {
            grid.add(new JLabel());
        }
        LocalDate today = LocalDate.now();
        for (int d = 1; d <= shownMonth.lengthOfMonth(); d++) {
            LocalDate date = shownMonth.atDay(d);
            JButton cell = new JButton(String.valueOf(d));
            cell.setFont(AppTheme.regular(12));
            cell.setMargin(new java.awt.Insets(2, 2, 2, 2));
            cell.setPreferredSize(new Dimension(34, 30));
            cell.setFocusable(false);
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            boolean selected = date.equals(pendingStart)
                    || (pendingStart == null && from != null && to != null
                            && !date.isBefore(from) && !date.isAfter(to));
            if (selected) {
                cell.setBackground(AppTheme.primary());
                cell.setForeground(Color.WHITE);
                cell.setFont(AppTheme.semibold(12));
            } else {
                cell.putClientProperty("JButton.buttonType", "borderless");
                if (date.equals(today)) {
                    cell.setForeground(AppTheme.primary());
                    cell.setFont(AppTheme.semibold(12));
                }
            }
            cell.addActionListener(event -> pick(date));
            grid.add(cell);
        }
        grid.revalidate();
        grid.repaint();
        popup.pack();
    }

    private void pick(LocalDate date) {
        if (pendingStart == null) {
            pendingStart = date;
            apply(date, date, date.format(LONG));
            hint.setText("Click another day to make a range.");
            renderMonth();
            return;
        }
        LocalDate start = date.isBefore(pendingStart) ? date : pendingStart;
        LocalDate end = date.isBefore(pendingStart) ? pendingStart : date;
        pendingStart = null;
        apply(start, end, start.equals(end) ? start.format(LONG)
                : start.format(SHORT) + " – " + end.format(LONG));
        popup.setVisible(false);
    }

    private void apply(LocalDate start, LocalDate end, String text) {
        from = start;
        to = end;
        label = text;
        updateText();
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private void updateText() {
        setText((isActive() ? "Date: " : "") + label);
        setForeground(isActive() ? AppTheme.primary() : AppTheme.foreground());
    }

    /** Small down chevron drawn in the button's text color. */
    private final class ChevronIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics graphics, int x, int y) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getForeground());
            g.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g.drawPolyline(new int[] { x + 1, x + 5, x + 9 }, new int[] { y + 3, y + 7, y + 3 }, 3);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }
    }
}
