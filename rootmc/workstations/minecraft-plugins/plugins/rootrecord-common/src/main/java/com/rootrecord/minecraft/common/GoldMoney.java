package com.rootrecord.minecraft.common;

import java.text.DecimalFormat;
import java.util.Locale;

/** Canonical Gold (G) rounding and player-facing display — three decimal places. */
public final class GoldMoney {

    public static final int DECIMALS = 3;
    public static final double SCALE = 1000.0;
    public static final double MIN_AMOUNT = 0.001;
    public static final String FORMAT_PATTERN = "%." + DECIMALS + "f";

    private static final DecimalFormat DISPLAY = new DecimalFormat("0.000");

    private GoldMoney() {}

    public static double round(double value) {
        return Math.round(value * SCALE) / SCALE;
    }

    public static String format(double value) {
        synchronized (DISPLAY) {
            return DISPLAY.format(value);
        }
    }

    public static String format(Locale locale, double value) {
        return String.format(locale, FORMAT_PATTERN, round(value));
    }
}
