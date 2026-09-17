package com.rootrecord.minecraft.rootskills.skills;

/**
 * Retro exponential XP curve.
 * <pre>
 * xpToNext(level) = multiplier * level^exponent + base
 * </pre>
 * When {@code cumulative} is true, total XP required to reach {@code level}
 * is the sum of {@code xpToNext(0) + … + xpToNext(level - 1)} (Cumulative_Curve style).
 */
public final class XpFormula {

    private final double multiplier;
    private final double exponent;
    private final double base;
    private final boolean cumulative;
    private final double globalXpMultiplier;

    public XpFormula(
            double multiplier,
            double exponent,
            double base,
            boolean cumulative,
            double globalXpMultiplier) {
        this.multiplier = multiplier;
        this.exponent = exponent;
        this.base = base;
        this.cumulative = cumulative;
        this.globalXpMultiplier = globalXpMultiplier <= 0 ? 1.0 : globalXpMultiplier;
    }

    public static XpFormula defaults() {
        // PROP-01 proportional climb — matches config.yml formula defaults
        return new XpFormula(1.6, 2.35, 500, true, 0.42);
    }

    public double multiplier() {
        return multiplier;
    }

    public double exponent() {
        return exponent;
    }

    public double base() {
        return base;
    }

    public boolean cumulative() {
        return cumulative;
    }

    public double globalXpMultiplier() {
        return globalXpMultiplier;
    }

    /**
     * XP required to advance from {@code level} to {@code level + 1}
     * when not using the cumulative total helper.
     */
    public long xpToNext(int level) {
        int safe = Math.max(0, level);
        double raw = multiplier * Math.pow(safe, exponent) + base;
        return Math.max(1L, (long) Math.floor(raw));
    }

    /**
     * Total XP required to reach {@code level} from 0.
     * When {@link #cumulative} is true (Cumulative_Curve), this is the sum of
     * {@code xpToNext(0) + … + xpToNext(level - 1)}. When false, the same sum is used for
     * absolute XP tracking; callers may instead treat each level band independently via
     * {@link #xpToNext(int)} alone.
     */
    public long totalXpForLevel(int level) {
        if (level <= 0) {
            return 0L;
        }
        long sum = 0L;
        for (int i = 0; i < level; i++) {
            sum += xpToNext(i);
        }
        return sum;
    }

    /**
     * XP still needed inside the current level band to reach {@code level + 1},
     * given total lifetime XP in the skill.
     */
    public long xpRemainingInLevel(int level, long totalXp) {
        long intoNext = xpToNext(level);
        long floor = totalXpForLevel(level);
        long progress = Math.max(0L, totalXp - floor);
        return Math.max(0L, intoNext - progress);
    }

    public int levelForTotalXp(long totalXp) {
        if (totalXp <= 0) {
            return 0;
        }
        int level = 0;
        long spent = 0L;
        // Soft cap guard — prestige threshold is typically 1000
        while (level < 10_000) {
            long need = xpToNext(level);
            if (spent + need > totalXp) {
                break;
            }
            spent += need;
            level++;
        }
        return level;
    }

    public long applyGlobalMultiplier(long rawXp) {
        if (rawXp <= 0) {
            return 0L;
        }
        return Math.max(0L, (long) Math.floor(rawXp * globalXpMultiplier));
    }
}
