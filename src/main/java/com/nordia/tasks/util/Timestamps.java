package com.nordia.tasks.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Always UTC with exactly 3 decimals: Instant.toString() drops them when they are zero. */
public final class Timestamps {

    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Timestamps() {}

    public static String format(Instant instant) {
        return UTC_MILLIS.format(instant);
    }

    public static String format(OffsetDateTime time) {
        return format(time.toInstant());
    }
}