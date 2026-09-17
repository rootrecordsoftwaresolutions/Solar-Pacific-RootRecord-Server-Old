package com.rootrecord.minecraft.rootactivity.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PeakHoursFormatter {

    private PeakHoursFormatter() {}

    /**
     * Remap zone-local hour buckets into another clock.
     * {@code viewerLocalHour = zoneLocalHour + (viewerOffset - zoneOffset)}.
     */
    public static int[] shiftToOffset(int[] zoneLocalBuckets, int zoneOffsetMinutes, int viewerOffsetMinutes) {
        int[] out = new int[24];
        if (zoneLocalBuckets == null || zoneLocalBuckets.length != 24) {
            return out;
        }
        int shiftHours = Math.floorDiv(viewerOffsetMinutes - zoneOffsetMinutes, 60);
        for (int h = 0; h < 24; h++) {
            int viewerHour = Math.floorMod(h + shiftHours, 24);
            out[viewerHour] = (int) Math.min(Integer.MAX_VALUE, (long) out[viewerHour] + zoneLocalBuckets[h]);
        }
        return out;
    }

    public static void addBuckets(int[] dest, int[] src) {
        if (dest == null || src == null || dest.length != 24 || src.length != 24) {
            return;
        }
        for (int h = 0; h < 24; h++) {
            dest[h] = (int) Math.min(Integer.MAX_VALUE, (long) dest[h] + src[h]);
        }
    }

    public static String format(int[] buckets, int peakHourCount) {
        if (buckets == null || buckets.length != 24) {
            return "";
        }
        int max = 0;
        for (int v : buckets) {
            max = Math.max(max, v);
        }
        if (max <= 0) {
            return "";
        }

        List<Integer> ranked = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            ranked.add(h);
        }
        ranked.sort(Comparator.comparingInt((Integer h) -> buckets[h]).reversed().thenComparingInt(h -> h));

        List<Integer> picks = new ArrayList<>();
        for (int h : ranked) {
            if (buckets[h] <= 0) {
                continue;
            }
            picks.add(h);
            if (picks.size() >= Math.max(1, peakHourCount)) {
                break;
            }
        }
        if (picks.isEmpty()) {
            return "";
        }
        picks.sort(Integer::compareTo);

        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < picks.size()) {
            int start = picks.get(i);
            int end = start;
            while (i + 1 < picks.size() && picks.get(i + 1) == end + 1) {
                i++;
                end = picks.get(i);
            }
            if (start == end) {
                parts.add(hourLabel(start));
            } else {
                parts.add(hourLabel(start) + " – " + hourLabel(end));
            }
            i++;
        }
        return String.join(", ", parts);
    }

    public static String hourLabel(int hour) {
        int h = ((hour % 24) + 24) % 24;
        if (h == 0) {
            return "12 AM";
        }
        if (h < 12) {
            return h + " AM";
        }
        if (h == 12) {
            return "12 PM";
        }
        return (h - 12) + " PM";
    }
}
