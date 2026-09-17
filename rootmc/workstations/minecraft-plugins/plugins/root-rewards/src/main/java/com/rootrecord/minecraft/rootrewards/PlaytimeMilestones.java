package com.rootrecord.minecraft.rootrewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Playtime thresholds and gold payouts (tier 0 = 15m/1G, tier 1 = 1h/2G, then double both). */
public final class PlaytimeMilestones {

    public record Milestone(int tier, long thresholdSeconds, long gold, String label) {}

    private static final List<Milestone> DEFAULT = buildDefault();

    private PlaytimeMilestones() {}

    public static List<Milestone> defaults() {
        return DEFAULT;
    }

    private static List<Milestone> buildDefault() {
        List<Milestone> out = new ArrayList<>();
        out.add(new Milestone(0, 15L * 60L, 1L, "15 minutes"));
        out.add(new Milestone(1, 3600L, 2L, "1 hour"));
        long seconds = 7200L;
        long gold = 4L;
        for (int tier = 2; tier <= 17; tier++) {
            out.add(new Milestone(tier, seconds, gold, formatDuration(seconds)));
            seconds *= 2L;
            gold *= 2L;
        }
        return Collections.unmodifiableList(out);
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds < 3600L) {
            return (totalSeconds / 60L) + " minutes";
        }
        if (totalSeconds % 3600L == 0L) {
            long hours = totalSeconds / 3600L;
            return hours + (hours == 1L ? " hour" : " hours");
        }
        long hours = totalSeconds / 3600L;
        long mins = (totalSeconds % 3600L) / 60L;
        return hours + "h " + mins + "m";
    }

    public static String formatRemaining(long totalSeconds, long targetSeconds) {
        long remaining = Math.max(0L, targetSeconds - totalSeconds);
        return formatDuration(remaining);
    }
}
