package com.rootrecord.minecraft.rootactivity.timezone;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record TimezoneDef(String key, String label, int offsetMinutes) {

    private static final List<TimezoneDef> ALL = List.of(
            new TimezoneDef("utc_minus_12", "UTC-12 (AoE)", -12 * 60),
            new TimezoneDef("utc_minus_11", "UTC-11 (NUT/SST)", -11 * 60),
            new TimezoneDef("utc_minus_10", "UTC-10 (HST)", -10 * 60),
            new TimezoneDef("utc_minus_9", "UTC-09 (AKST)", -9 * 60),
            new TimezoneDef("utc_minus_8", "UTC-08 (PST)", -8 * 60),
            new TimezoneDef("utc_minus_7", "UTC-07 (MST)", -7 * 60),
            new TimezoneDef("utc_minus_6", "UTC-06 (CST)", -6 * 60),
            new TimezoneDef("utc_minus_5", "UTC-05 (EST)", -5 * 60),
            new TimezoneDef("utc_minus_4", "UTC-04 (AST)", -4 * 60),
            new TimezoneDef("utc_minus_3", "UTC-03 (BRT)", -3 * 60),
            new TimezoneDef("utc_minus_2", "UTC-02 (GST)", -2 * 60),
            new TimezoneDef("utc_minus_1", "UTC-01 (AZOT)", -1 * 60),
            new TimezoneDef("utc_plus_0", "UTC+00 (GMT/UTC)", 0),
            new TimezoneDef("utc_plus_1", "UTC+01 (CET)", 60),
            new TimezoneDef("utc_plus_2", "UTC+02 (EET)", 2 * 60),
            new TimezoneDef("utc_plus_3", "UTC+03 (MSK/AST)", 3 * 60),
            new TimezoneDef("utc_plus_4", "UTC+04 (GST)", 4 * 60),
            new TimezoneDef("utc_plus_5", "UTC+05 (PKT)", 5 * 60),
            new TimezoneDef("utc_plus_6", "UTC+06 (BST)", 6 * 60),
            new TimezoneDef("utc_plus_7", "UTC+07 (ICT)", 7 * 60),
            new TimezoneDef("utc_plus_8", "UTC+08 (CST/SGT)", 8 * 60),
            new TimezoneDef("utc_plus_9", "UTC+09 (JST/KST)", 9 * 60),
            new TimezoneDef("utc_plus_10", "UTC+10 (AEST)", 10 * 60),
            new TimezoneDef("utc_plus_11", "UTC+11 (AEDT/SBT)", 11 * 60),
            new TimezoneDef("utc_plus_12", "UTC+12 (NZST/FJT)", 12 * 60),
            new TimezoneDef("utc_plus_13", "UTC+13 (NZDT)", 13 * 60),
            new TimezoneDef("utc_plus_14", "UTC+14 (LINT)", 14 * 60));

    public static List<TimezoneDef> all() {
        return ALL;
    }

    public static Optional<TimezoneDef> byKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return ALL.stream().filter(t -> t.key.equals(key)).findFirst();
    }

    public static Optional<TimezoneDef> nearestOffsetMinutes(int offsetMinutes) {
        TimezoneDef best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (TimezoneDef def : ALL) {
            int diff = Math.abs(def.offsetMinutes - offsetMinutes);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = def;
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<TimezoneDef> nearestOffsetSeconds(int offsetSeconds) {
        return nearestOffsetMinutes(Math.round(offsetSeconds / 60f));
    }

    public static int[] emptyHourBuckets() {
        return new int[24];
    }
}
