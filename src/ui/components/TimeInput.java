package src.ui.components;

import src.ui.AppTheme;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Time field (hh:mm) with its own AM/PM dropdown. */
public class TimeInput extends JPanel {

    private final JTextField clock = new JTextField();
    private final JComboBox<String> meridiem = new JComboBox<>(new String[] { "AM", "PM" });

    public TimeInput() {
        super(new BorderLayout(6, 0));
        setOpaque(false);
        clock.putClientProperty("JTextField.placeholderText", "hh:mm");
        clock.setToolTipText("Hour and minutes, e.g. 09:30");
        meridiem.setToolTipText("Select AM or PM");
        meridiem.setFont(AppTheme.regular(12));
        meridiem.setPreferredSize(new Dimension(78, meridiem.getPreferredSize().height));
        add(clock, BorderLayout.CENTER);
        add(meridiem, BorderLayout.EAST);
    }

    /** Returns the time as "hh:mm AM"; a typed 24-hour or full value is kept as typed. */
    public String getText() {
        String text = clock.getText() == null ? "" : clock.getText().trim().toUpperCase(Locale.ROOT);
        if (text.endsWith("AM") || text.endsWith("PM")) {
            return text;
        }
        int colon = text.indexOf(':');
        if (colon > 0) {
            try {
                int hour = Integer.parseInt(text.substring(0, colon).trim());
                if (hour == 0 || hour > 12) {
                    return text;
                }
            } catch (NumberFormatException ignored) {
                return text;
            }
        }
        return text + " " + meridiem.getSelectedItem();
    }

    /** Accepts "hh:mm AM" or "hh:mm"; the AM/PM part selects the dropdown. */
    public void setText(String value) {
        String text = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (text.endsWith("AM") || text.endsWith("PM")) {
            meridiem.setSelectedItem(text.substring(text.length() - 2));
            text = text.substring(0, text.length() - 2).trim();
        }
        clock.setText(text);
    }

    public void setTime(LocalTime time) {
        setText(time.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)));
    }

    public void addDocumentListener(DocumentListener listener) {
        clock.getDocument().addDocumentListener(listener);
        meridiem.addActionListener(event -> listener.changedUpdate(null));
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        if (clock != null) {
            clock.setFont(font);
        }
    }

    @Override
    public void setForeground(Color color) {
        super.setForeground(color);
        if (clock != null) {
            clock.setForeground(color);
        }
    }

    @Override
    public void setBackground(Color color) {
        super.setBackground(color);
        if (clock != null) {
            clock.setBackground(color);
        }
    }
}
