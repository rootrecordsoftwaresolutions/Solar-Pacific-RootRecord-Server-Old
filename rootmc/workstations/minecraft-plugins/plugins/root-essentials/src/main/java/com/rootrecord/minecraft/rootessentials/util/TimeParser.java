package com.rootrecord.minecraft.rootessentials.util;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser {

    private static final Pattern DURATION = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    private TimeParser() {}

    public static Instant parseExpiry(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = DURATION.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return null;
        }
        long amount = Long.parseLong(m.group(1));
        long seconds = switch (m.group(2)) {
            case "s" -> amount;
            case "m" -> amount * 60L;
            case "h" -> amount * 3600L;
            case "d" -> amount * 86400L;
            case "w" -> amount * 604800L;
            default -> -1L;
        };
        if (seconds <= 0) {
            return null;
        }
        return Instant.now().plusSeconds(seconds);
    }

    /** Compact relative duration for /seen (e.g. {@code 2d 23h}, {@code 5h 30m}, {@code 12m}). */
    public static String formatAgo(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long totalSeconds = millis / 1000L;
        if (totalSeconds < 60) {
            return totalSeconds <= 0 ? "<1m" : totalSeconds + "s";
        }
        long totalMinutes = totalSeconds / 60L;
        if (totalMinutes < 60) {
            return totalMinutes + "m";
        }
        long days = totalMinutes / (60L * 24L);
        long hours = (totalMinutes % (60L * 24L)) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0) {
            if (hours > 0) {
                return days + "d " + hours + "h";
            }
            return days + "d";
        }
        if (minutes > 0) {
            return hours + "h " + minutes + "m";
        }
        return hours + "h";
    }
}
