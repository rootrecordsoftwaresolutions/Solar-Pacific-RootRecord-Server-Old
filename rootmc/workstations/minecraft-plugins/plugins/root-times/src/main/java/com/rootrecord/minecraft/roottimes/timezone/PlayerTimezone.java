package com.rootrecord.minecraft.roottimes.timezone;

import java.util.List;
import java.util.Optional;

/** Fixed-offset keys aligned with Root-Activity TimezoneDef (no hard dep). */
public record PlayerTimezone(String key, String label, int offsetMinutes) {

    private static final List<PlayerTimezone> ALL = List.of(
            new PlayerTimezone("utc_minus_12", "UTC-12 (AoE)", -12 * 60),
            new PlayerTimezone("utc_minus_11", "UTC-11 (NUT/SST)", -11 * 60),
            new PlayerTimezone("utc_minus_10", "UTC-10 (HST)", -10 * 60),
            new PlayerTimezone("utc_minus_9", "UTC-09 (AKST)", -9 * 60),
            new PlayerTimezone("utc_minus_8", "UTC-08 (PST)", -8 * 60),
            new PlayerTimezone("utc_minus_7", "UTC-07 (MST)", -7 * 60),
            new PlayerTimezone("utc_minus_6", "UTC-06 (CST)", -6 * 60),
            new PlayerTimezone("utc_minus_5", "UTC-05 (EST)", -5 * 60),
            new PlayerTimezone("utc_minus_4", "UTC-04 (AST)", -4 * 60),
            new PlayerTimezone("utc_minus_3", "UTC-03 (BRT)", -3 * 60),
            new PlayerTimezone("utc_minus_2", "UTC-02 (GST)", -2 * 60),
            new PlayerTimezone("utc_minus_1", "UTC-01 (AZOT)", -1 * 60),
            new PlayerTimezone("utc_plus_0", "UTC+00 (GMT/UTC)", 0),
            new PlayerTimezone("utc_plus_1", "UTC+01 (CET)", 60),
            new PlayerTimezone("utc_plus_2", "UTC+02 (EET)", 2 * 60),
            new PlayerTimezone("utc_plus_3", "UTC+03 (MSK/AST)", 3 * 60),
            new PlayerTimezone("utc_plus_4", "UTC+04 (GST)", 4 * 60),
            new PlayerTimezone("utc_plus_5", "UTC+05 (PKT)", 5 * 60),
            new PlayerTimezone("utc_plus_6", "UTC+06 (BST)", 6 * 60),
            new PlayerTimezone("utc_plus_7", "UTC+07 (ICT)", 7 * 60),
            new PlayerTimezone("utc_plus_8", "UTC+08 (CST/SGT)", 8 * 60),
            new PlayerTimezone("utc_plus_9", "UTC+09 (JST/KST)", 9 * 60),
            new PlayerTimezone("utc_plus_10", "UTC+10 (AEST)", 10 * 60),
            new PlayerTimezone("utc_plus_11", "UTC+11 (AEDT/SBT)", 11 * 60),
            new PlayerTimezone("utc_plus_12", "UTC+12 (NZST/FJT)", 12 * 60),
            new PlayerTimezone("utc_plus_13", "UTC+13 (NZDT)", 13 * 60),
            new PlayerTimezone("utc_plus_14", "UTC+14 (LINT)", 14 * 60));

    public static Optional<PlayerTimezone> byKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeKey(key);
        return ALL.stream().filter(t -> t.key.equals(normalized)).findFirst();
    }

    public static PlayerTimezone utc() {
        return byKey("utc_plus_0").orElse(ALL.get(12));
    }

    /** Nearest fixed-offset key for an IP-API offset (seconds east of UTC). */
    public static Optional<PlayerTimezone> nearestOffsetSeconds(int offsetSeconds) {
        int minutes = Math.round(offsetSeconds / 60f);
        PlayerTimezone best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (PlayerTimezone tz : ALL) {
            int delta = Math.abs(tz.offsetMinutes() - minutes);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = tz;
            }
        }
        return Optional.ofNullable(best);
    }

    /** @deprecated use {@link #utc()} */
    @Deprecated
    public static PlayerTimezone hst() {
        return byKey("utc_minus_10").orElse(ALL.get(2));
    }

    public static List<PlayerTimezone> all() {
        return ALL;
    }

    private static String normalizeKey(String raw) {
        String k = raw.trim().toLowerCase();
        return switch (k) {
            case "hst", "utc-10", "utc_minus_10", "utcminus10", "hawaii" -> "utc_minus_10";
            case "utc", "gmt", "utc+0", "utc_plus_0", "utc0" -> "utc_plus_0";
            default -> k;
        };
    }
}
