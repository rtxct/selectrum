package com.selectrum.domain.model;

import com.selectrum.utils.TimeUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

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

    private final String hour;
    private final String theme;
    private final String editor;
    private final LocalTime parsedTime;

    public ScheduleEntry(String hour, String theme, String editor) {
        this.hour = Objects.requireNonNull(hour, "hour must not be null");
        this.theme = Objects.requireNonNull(theme, "theme must not be null");

        this.editor = editor;
        this.parsedTime = TimeUtils.parseTime(hour);
    }

    public String getEffectiveEditor() {
        return editor != null ? editor : theme;
    }
}