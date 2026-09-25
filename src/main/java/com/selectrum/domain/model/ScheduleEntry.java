package com.selectrum.domain.model;

import com.selectrum.utils.TimeUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
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
@Getter
@EqualsAndHashCode
public final class ScheduleEntry {

    /**
     * The configured hour in 24-hour format.
     */
    private final String hour;

    /**
     * The name of the IDE theme to apply at the specified time.
     */
    private final String theme;

    /**
     * The name of the editor color scheme to apply, or {@code null} if not specified.
     */
    private final String editor;

    /**
     * The parsed representation of the hour as a {@link LocalTime} object.
     */
    private final LocalTime parsedTime;

    /**
     * Constructs a new {@code ScheduleEntry} with the specified time, theme, and optional editor scheme.
     *
     * @param hour   the time of day in 24-hour format
     * @param theme  the name of the IDE theme
     * @param editor the name of the editor color scheme, or {@code null}
     * @throws NullPointerException if {@code hour} or {@code theme} is null
     */
    public ScheduleEntry(@NotNull String hour, @NotNull String theme, @NotNull String editor) {
        this.hour = Objects.requireNonNull(hour, "hour must not be null");
        this.theme = Objects.requireNonNull(theme, "theme must not be null");

        this.editor = editor;
        this.parsedTime = TimeUtils.parseTime(hour);
    }

    /**
     * Gets the effective editor color scheme to use.
     *
     * @return the explicitly configured editor scheme if present, otherwise the theme name
     */
    public @NotNull String getEffectiveEditor() {
        return editor != null ? editor : theme;
    }
}