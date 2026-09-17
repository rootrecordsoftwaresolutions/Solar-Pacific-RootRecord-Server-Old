package com.rootrecord.minecraft.rootloans.config;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record LoansConfig(
        boolean mysqlEnabled,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String mysqlTablePrefix,
        String mysqlJdbcParams,
        boolean enabled,
        double interestRate,
        double defaultMaxLoan,
        boolean rankLimitsEnabled,
        String maxCapMode,
        int maxTakesPer24h,
        int maxConcurrentLoans,
        double incomeSweepPercent,
        boolean goldOreRepayment) {

    public static LoansConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        double defaultMax = cfg.getDouble("loan.default-max-loan",
                cfg.getDouble("loan.starting-max-loan", 100.0));
        String capMode = cfg.getString("loan.max-cap-mode", "rank").trim().toLowerCase();
        return new LoansConfig(
                db.enabled(),
                db.host(),
                db.port(),
                db.database(),
                db.username(),
                db.password(),
                db.tablePrefix(),
                db.jdbcParams(),
                cfg.getBoolean("loan.enabled", true),
                Math.max(0, cfg.getDouble("loan.interest-rate", 0.10)),
                Math.max(0, defaultMax),
                cfg.getBoolean("loan.rank-limits-enabled", true),
                capMode,
                Math.max(1, cfg.getInt("loan.max-takes-per-24h", 3)),
                Math.max(1, cfg.getInt("loan.max-concurrent-loans", 3)),
                Math.min(1.0, Math.max(0, cfg.getDouble("loan.income-sweep-percent", 1.0))),
                cfg.getBoolean("loan.gold-ore-repayment", true));
    }

    /** @deprecated use {@link #defaultMaxLoan()} */
    @Deprecated
    public double startingMaxLoan() {
        return defaultMaxLoan;
    }

    public String activeTable() {
        return mysqlTablePrefix + "loans_active";
    }

    public String creditTable() {
        return mysqlTablePrefix + "loans_credit";
    }

    public String takeLogTable() {
        return mysqlTablePrefix + "loans_take_log";
    }
}
