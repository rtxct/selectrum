package com.selectrum.utils;

import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for time-related operations.
 * Provides methods to parse time strings into {@link LocalTime} objects.
 */
public class TimeUtils {

    /**
     * Formatter used for parsing time strings in the format "H:mm" or "HH:mm".
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    /**
     * Parses a string representation of time into a {@link LocalTime} object.
     * The input string is expected to be in the format "H:mm" or "HH:mm" (24-hour).
     *
     * @param hour the string representing the time to parse
     * @return the parsed {@link LocalTime} object
     * @throws IllegalArgumentException if the provided string cannot be parsed into a valid time
     */
    public static @NotNull LocalTime parseTime(@NotNull String hour) {
        try {
            return LocalTime.parse(hour, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid hour format: '" + hour + "'. Expected H:mm or HH:mm (24-hour).", e);
        }
    }
}