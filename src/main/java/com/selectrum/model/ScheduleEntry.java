package com.selectrum.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * A single schedule entry mapping a time of day to an IDE theme and optional editor color scheme.
 * <p>
 * The {@code hour} field accepts 24-hour format ({@code H:mm}),
 * supporting both {@code "8:30"} and {@code "08:30"}.
 * <p>
 * The {@code editor} field is optional. When {@code null}, the scheduler will attempt
 * to find an editor color scheme matching the theme name.
 */
public final class ScheduleEntry {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private final String hour;
    private final String theme;
    private final String editor;
    private final LocalTime parsedTime;

    public ScheduleEntry(String hour, String theme, String editor) {
        this.hour = Objects.requireNonNull(hour, "hour must not be null");
        this.theme = Objects.requireNonNull(theme, "theme must not be null");
        this.editor = editor; // nullable — falls back to theme name
        this.parsedTime = parseTime(hour);
    }

    private static LocalTime parseTime(String hour) {
        try {
            return LocalTime.parse(hour, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid hour format: '" + hour + "'. Expected H:mm or HH:mm (24-hour).", e);
        }
    }

    public String getHour() {
        return hour;
    }

    public String getTheme() {
        return theme;
    }

    public LocalTime getParsedTime() {
        return parsedTime;
    }

    /**
     * Returns the editor color scheme name, or {@code null} if not specified.
     * When {@code null}, the scheduler should fall back to a scheme matching the theme name.
     */
    public String getEditor() {
        return editor;
    }

    /**
     * Returns the effective editor color scheme name to look up.
     * Uses the explicit {@code editor} value if set, otherwise falls back to the theme name.
     */
    public String getEffectiveEditor() {
        return editor != null ? editor : theme;
    }

    @Override
    public String toString() {
        return "ScheduleEntry{hour='" + hour + "', theme='" + theme
                + "', editor='" + (editor != null ? editor : "<inherit>") + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScheduleEntry that)) return false;
        return hour.equals(that.hour) && theme.equals(that.theme) && Objects.equals(editor, that.editor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hour, theme, editor);
    }
}
