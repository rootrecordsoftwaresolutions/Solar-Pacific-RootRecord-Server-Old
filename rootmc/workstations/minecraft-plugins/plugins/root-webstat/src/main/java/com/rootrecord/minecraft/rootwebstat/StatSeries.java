package com.rootrecord.minecraft.rootwebstat;

import java.util.Arrays;
import java.util.Locale;

/** Descriptive stats for one numeric series. */
public final class StatSeries {

    private final String id;
    private final String unit;
    private final int count;
    private final double total;
    private final double average;
    private final double mean;
    private final double median;
    private final double highest;
    private final double lowest;
    private final String highestLabel;
    private final String lowestLabel;

    public StatSeries(
            String id,
            String unit,
            int count,
            double total,
            double average,
            double mean,
            double median,
            double highest,
            double lowest,
            String highestLabel,
            String lowestLabel) {
        this.id = id;
        this.unit = unit;
        this.count = count;
        this.total = total;
        this.average = average;
        this.mean = mean;
        this.median = median;
        this.highest = highest;
        this.lowest = lowest;
        this.highestLabel = highestLabel;
        this.lowestLabel = lowestLabel;
    }

    public static StatSeries empty(String id, String unit) {
        return new StatSeries(id, unit, 0, 0, 0, 0, 0, 0, 0, "", "");
    }

    /** {@code values} paired with {@code labels} (same length). Mutates a sorted copy of values. */
    public static StatSeries from(String id, String unit, double[] values, String[] labels) {
        if (values == null || values.length == 0) {
            return empty(id, unit);
        }
        int n = values.length;
        double[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        double total = 0;
        for (double v : values) {
            total += v;
        }
        double mean = total / n;
        double median;
        if ((n & 1) == 1) {
            median = sorted[n / 2];
        } else {
            median = (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        }
        double highest = sorted[n - 1];
        double lowest = sorted[0];
        String highLabel = "";
        String lowLabel = "";
        if (labels != null && labels.length == n) {
            for (int i = 0; i < n; i++) {
                if (values[i] == highest && highLabel.isEmpty()) {
                    highLabel = labels[i] == null ? "" : labels[i];
                }
                if (values[i] == lowest && lowLabel.isEmpty()) {
                    lowLabel = labels[i] == null ? "" : labels[i];
                }
            }
        }
        return new StatSeries(
                id, unit, n, total, mean, mean, median, highest, lowest, highLabel, lowLabel);
    }

    public String toJsonObject() {
        return "{"
                + "\"id\":\"" + esc(id) + "\","
                + "\"unit\":\"" + esc(unit) + "\","
                + "\"count\":" + count + ","
                + "\"total\":" + num(total) + ","
                + "\"average\":" + num(average) + ","
                + "\"mean\":" + num(mean) + ","
                + "\"median\":" + num(median) + ","
                + "\"highest\":" + num(highest) + ","
                + "\"lowest\":" + num(lowest) + ","
                + "\"highest_holder\":\"" + esc(highestLabel) + "\","
                + "\"lowest_holder\":\"" + esc(lowestLabel) + "\""
                + "}";
    }

    private static String num(double v) {
        if (!Double.isFinite(v)) {
            return "0";
        }
        return String.format(Locale.US, "%.6f", v);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public String id() {
        return id;
    }

    public int count() {
        return count;
    }

    public double total() {
        return total;
    }
}
