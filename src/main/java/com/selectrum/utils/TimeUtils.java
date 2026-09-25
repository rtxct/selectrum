package com.selectrum.utils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class TimeUtils {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    public static LocalTime parseTime(String hour) {
        try {
            return LocalTime.parse(hour, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid hour format: '" + hour + "'. Expected H:mm or HH:mm (24-hour).", e);
        }
    }
}