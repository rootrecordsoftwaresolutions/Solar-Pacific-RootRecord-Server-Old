package com.rootrecord.minecraft.rootloans.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;

public record LoanDailyTaxConfig(
        boolean enabled,
        double rate,
        int scheduleHourHst,
        int pollSeconds,
        double minAccrual) {

    public static final ZoneId HST = ZoneId.of("Pacific/Honolulu");

    public static LoanDailyTaxConfig from(FileConfiguration cfg) {
        return new LoanDailyTaxConfig(
                cfg.getBoolean("loan-daily-tax.enabled", true),
                Math.max(0, cfg.getDouble("loan-daily-tax.rate", 0.01)),
                Math.max(0, Math.min(23, cfg.getInt("loan-daily-tax.schedule-hour-hst", 0))),
                Math.max(60, cfg.getInt("loan-daily-tax.poll-seconds", 300)),
                Math.max(0, cfg.getDouble("loan-daily-tax.min-accrual", 0.01)));
    }
}
