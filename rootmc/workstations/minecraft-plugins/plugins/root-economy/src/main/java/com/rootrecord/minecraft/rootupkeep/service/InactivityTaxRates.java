package com.rootrecord.minecraft.rootupkeep.service;

import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class InactivityTaxRates {

    private InactivityTaxRates() {}

    public record Tier(double rateFraction, double ratePercent, long inactiveDays) {}

    public static Tier tierFor(UpkeepConfig cfg, Instant lastActive) {
        if (lastActive == null) {
            return null;
        }
        long inactiveDays = ChronoUnit.DAYS.between(lastActive, Instant.now());
        if (inactiveDays < cfg.graceDays()) {
            return null;
        }
        long taxedDays = inactiveDays - cfg.graceDays();
        double percent;
        if (taxedDays < 7) {
            percent = cfg.week1RatePercent();
        } else if (taxedDays < 14) {
            percent = cfg.week2RatePercent();
        } else if (taxedDays < 21) {
            percent = cfg.week3RatePercent();
        } else {
            percent = cfg.week4RatePercent();
        }
        return new Tier(percent / 100.0, percent, inactiveDays);
    }

    public static double taxAmount(UpkeepConfig cfg, double balance, Tier tier) {
        if (tier == null || balance < cfg.minBalance()) {
            return 0;
        }
        double tax = balance * tier.rateFraction();
        if (tax < cfg.minTax()) {
            return 0;
        }
        return Math.min(tax, balance);
    }
}
