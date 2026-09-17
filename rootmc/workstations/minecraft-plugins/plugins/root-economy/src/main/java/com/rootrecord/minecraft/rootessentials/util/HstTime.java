package com.rootrecord.minecraft.rootessentials.util;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Hawaii Standard Time month boundaries (matches rootmc-realm-api treasury cron). */
public final class HstTime {

    public static final ZoneId HST = ZoneId.of("Pacific/Honolulu");

    private HstTime() {}

    public static String currentMonthKey() {
        return YearMonth.now(HST).toString();
    }

    public static String previousMonthKey() {
        return YearMonth.now(HST).minusMonths(1).toString();
    }

    /** UTC instant for HST midnight on the first day of the month. */
    public static Instant monthStartUtc(String monthKey) {
        YearMonth ym = YearMonth.parse(monthKey);
        return ym.atDay(1).atStartOfDay(HST).toInstant();
    }

    public static Instant monthEndUtc(String monthKey) {
        YearMonth ym = YearMonth.parse(monthKey);
        return ym.plusMonths(1).atDay(1).atStartOfDay(HST).toInstant();
    }

    public static String formatPlaytime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        return m + "m";
    }

    public static String formatMonthLabel(String monthKey) {
        YearMonth ym = YearMonth.parse(monthKey);
        ZonedDateTime z = ym.atDay(1).atStartOfDay(HST);
        return ym.getMonth().name().charAt(0)
                + ym.getMonth().name().substring(1).toLowerCase()
                + " "
                + z.getYear()
                + " (HST)";
    }
}
