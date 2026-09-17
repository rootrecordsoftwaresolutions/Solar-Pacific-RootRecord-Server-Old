package com.rootrecord.minecraft.common;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Configurable Minecraft-day clock: {@code length-minutes} real time per MC day,
 * aligned to a configured IANA timezone. Default midday minute = noon (6000),
 * midnight minute = midnight (18000). Day id rolls at each midnight minute.
 */
public final class McDayClock {

    public static final long TICKS_PER_DAY = 24_000L;
    public static final long NOON_TICK = 6_000L;
    public static final long MIDNIGHT_TICK = 18_000L;
    /** Vanilla daylight cycle length used to scale crop/random-tick growth. */
    public static final int VANILLA_DAY_MINUTES = 20;
    public static final int VANILLA_RANDOM_TICK_SPEED = 3;

    private static volatile boolean enabled;
    private static volatile ZoneId zone = ZoneId.of("UTC");
    private static volatile int middayMinute = 0;
    private static volatile int midnightMinute = 15;
    private static volatile int lengthMinutes = 30;
    /**
     * Absolute day id that maps to local day 0. {@code 0} = use absolute network days (Towny).
     * Claims sets this via {@code minecraft-day.day-id-base: auto}.
     */
    private static volatile long dayIdBase = 0L;

    private McDayClock() {}

    public static void configure(
            boolean active,
            String timezone,
            int lengthMinutes,
            int middayMinute,
            int midnightMinute) {
        configure(active, timezone, lengthMinutes, middayMinute, midnightMinute, 0L);
    }

    public static void configure(
            boolean active,
            String timezone,
            int lengthMinutes,
            int middayMinute,
            int midnightMinute,
            long dayIdBase) {
        enabled = active;
        if (timezone != null && !timezone.isBlank()) {
            zone = ZoneId.of(timezone.trim());
        }
        McDayClock.lengthMinutes = Math.max(1, lengthMinutes);
        McDayClock.middayMinute = Math.floorMod(middayMinute, 60);
        McDayClock.midnightMinute = Math.floorMod(midnightMinute, 60);
        McDayClock.dayIdBase = Math.max(0L, dayIdBase);
    }

    /** Override local day-0 anchor after timezone/length are configured. */
    public static void setDayIdBase(long dayIdBase) {
        McDayClock.dayIdBase = Math.max(0L, dayIdBase);
    }

    public static long dayIdBase() {
        return dayIdBase;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static ZoneId zone() {
        return zone;
    }

    public static int lengthMinutes() {
        return lengthMinutes;
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(zone);
    }

    /** Absolute wall-clock MC day (ignores {@link #dayIdBase}). */
    public static long absoluteDayId() {
        return absoluteDayId(now());
    }

    public static long absoluteDayId(ZonedDateTime hst) {
        if (!enabled) {
            return 0L;
        }
        long epochMinutes =
                hst.toLocalDate().toEpochDay() * 1_440L + hst.getHour() * 60L + hst.getMinute();
        return Math.floorDiv(epochMinutes - midnightMinute, lengthMinutes);
    }

    /** MC day id for this host; increments once per {@code length-minutes} (at in-game midnight). */
    public static long currentDayId() {
        return currentDayId(now());
    }

    public static long currentDayId(ZonedDateTime hst) {
        if (!enabled) {
            return 0L;
        }
        return Math.max(0L, absoluteDayId(hst) - dayIdBase);
    }

    /** Minutes elapsed since the last in-game midnight. */
    public static double minutesIntoDay(ZonedDateTime hst) {
        long minuteOfDay = hst.getHour() * 60L + hst.getMinute();
        long offset = lengthMinutes - midnightMinute;
        return (minuteOfDay + offset) % lengthMinutes + hst.getSecond() / 60.0;
    }

    /** Time-of-day in world ticks (0–23999). */
    public static long timeOfDayTicks(ZonedDateTime hst) {
        double min = minutesIntoDay(hst);
        double half = lengthMinutes / 2.0;
        if (min < half) {
            return MIDNIGHT_TICK + Math.round(TICKS_PER_DAY / 2.0 * (min / half));
        }
        long ticks = NOON_TICK + Math.round(TICKS_PER_DAY / 2.0 * ((min - half) / half));
        return Math.floorMod(ticks, TICKS_PER_DAY);
    }

    public static long fullTime(ZonedDateTime hst) {
        return currentDayId(hst) * TICKS_PER_DAY + timeOfDayTicks(hst);
    }

    public static long fullTime() {
        return fullTime(now());
    }

    /** Real milliseconds until the next in-game midnight (MC day / Towny rollover). */
    public static long millisUntilNextMidnight() {
        return millisUntilNextMidnight(now());
    }

    public static long millisUntilNextMidnight(ZonedDateTime hst) {
        if (!enabled) {
            return 0L;
        }
        double into = minutesIntoDay(hst);
        double remainingMin = lengthMinutes - into;
        if (remainingMin <= 0) {
            remainingMin = lengthMinutes;
        }
        return Math.max(0L, Math.round(remainingMin * 60_000.0));
    }

    /** Real minutes per vanilla ~20 min MC day (1.0 at 20 min, 1.5 at 30 min, 3.0 at 60 min). */
    public static double growthScale() {
        if (!enabled) {
            return 1.0;
        }
        return lengthMinutes / (double) VANILLA_DAY_MINUTES;
    }

    /** {@link org.bukkit.GameRule#RANDOM_TICK_SPEED} for crops, saplings, grass, etc. */
    public static int randomTickSpeed() {
        if (!enabled) {
            return VANILLA_RANDOM_TICK_SPEED;
        }
        int speed = (int) Math.round(VANILLA_RANDOM_TICK_SPEED * VANILLA_DAY_MINUTES / (double) lengthMinutes);
        return Math.max(1, speed);
    }

    /** Target Spigot {@code spigot.yml} growth *-modifier percent (100 = vanilla). */
    public static int spigotGrowthModifierPercent() {
        if (!enabled) {
            return 100;
        }
        int percent = (int) Math.round(100.0 * VANILLA_DAY_MINUTES / lengthMinutes);
        return Math.max(1, percent);
    }

    /** Scale Paper/Spigot tick intervals (higher = slower). */
    public static int scaledTickRate(int vanillaRate) {
        if (!enabled || vanillaRate <= 0) {
            return vanillaRate;
        }
        return Math.max(1, (int) Math.round(vanillaRate * growthScale()));
    }
}
