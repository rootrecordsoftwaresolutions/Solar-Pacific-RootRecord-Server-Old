package com.rootrecord.minecraft.rootloans.config;

import org.bukkit.configuration.file.FileConfiguration;

public record TownLoansConfig(
        boolean enabled,
        double maxLoan,
        double interestRate) {

    public static TownLoansConfig from(FileConfiguration cfg) {
        double serverRate = Math.max(0, cfg.getDouble("loan.interest-rate", 0.10));
        return new TownLoansConfig(
                cfg.getBoolean("town-loan.enabled", true),
                Math.max(0, cfg.getDouble("town-loan.max-loan", 500.0)),
                Math.max(0, cfg.getDouble("town-loan.interest-rate", serverRate)));
    }

    public String activeTable(String prefix) {
        return prefix + "town_loans_active";
    }
}
