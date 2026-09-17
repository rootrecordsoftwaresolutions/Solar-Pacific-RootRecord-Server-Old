package com.rootrecord.minecraft.rootbonds.config;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.GoldMoney;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record BondsConfig(
        boolean mysqlEnabled,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String mysqlTablePrefix,
        String mysqlJdbcParams,
        boolean enabled,
        double minPrincipalG,
        double incomeShare,
        int goldChestRows,
        long mcDayTicks,
        String dayWorld,
        boolean syncOnMcDay,
        int syncIntervalMinutes,
        int claimExpiryHours,
        boolean autoPayEarningsWallet,
        boolean autoBondGovernments,
        double bondedRootCostG,
        boolean bondedRootRegisterForEarnings,
        int graceDays,
        String playtimeTable,
        /** Max MC days settled with real payouts after downtime (~30 min/MC day). Older gap is skipped. */
        int catchUpMaxMcDays) {

    public static BondsConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        String prefix = db.tablePrefix();
        String playtimeRaw = cfg.getString("mysql.playtime-table", "rootmc_playtime").trim();
        String playtimeTable = playtimeRaw.startsWith(prefix) ? playtimeRaw : prefix + playtimeRaw;
        return new BondsConfig(
                db.enabled(),
                db.host(),
                db.port(),
                db.database(),
                db.username(),
                db.password(),
                prefix,
                db.jdbcParams(),
                cfg.getBoolean("bonds.enabled", true),
                Math.max(GoldMoney.MIN_AMOUNT, cfg.getDouble("bonds.min-principal-g", 1.0)),
                clamp01(cfg.getDouble("bonds.income-share", 0.25)),
                Math.max(1, Math.min(5, cfg.getInt("bonds.gold-chest-rows", 5))),
                Math.max(1L, cfg.getLong("bonds.mc-day-ticks", 24000L)),
                cfg.getString("bonds.day-world", "").trim(),
                cfg.getBoolean("bonds.sync-on-mc-day", true),
                Math.max(1, cfg.getInt("bonds.sync-interval-minutes", 5)),
                Math.max(1, cfg.getInt("bonds.claim-expiry-hours", 672)),
                cfg.getBoolean("bonds.auto-pay-earnings-wallet", true),
                cfg.getBoolean("bonds.auto-bond-governments", true),
                Math.max(GoldMoney.MIN_AMOUNT, cfg.getDouble("bonded-root.cost-g", 1000.0)),
                cfg.getBoolean("bonded-root.register-for-earnings", false),
                Math.max(1, cfg.getInt("activity.grace-days", 7)),
                playtimeTable,
                Math.max(1, cfg.getInt("bonds.catch-up-max-mc-days", 96)));
    }

    public BondsConfig withGraceDays(int graceDays) {
        return new BondsConfig(
                mysqlEnabled,
                mysqlHost,
                mysqlPort,
                mysqlDatabase,
                mysqlUsername,
                mysqlPassword,
                mysqlTablePrefix,
                mysqlJdbcParams,
                enabled,
                minPrincipalG,
                incomeShare,
                goldChestRows,
                mcDayTicks,
                dayWorld,
                syncOnMcDay,
                syncIntervalMinutes,
                claimExpiryHours,
                autoPayEarningsWallet,
                autoBondGovernments,
                bondedRootCostG,
                bondedRootRegisterForEarnings,
                Math.max(1, graceDays),
                playtimeTable,
                catchUpMaxMcDays);
    }

    /** Days without login before bond notes are auto-redeemed (derived from claim-expiry-hours). */
    public int inactivityForfeitDays() {
        return Math.max(1, claimExpiryHours / 24);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    public String bondsTable() {
        return mysqlTablePrefix + "bonds";
    }

    public String accruedTable() {
        return mysqlTablePrefix + "bonds_accrued";
    }

    public String dayInflowTable() {
        return mysqlTablePrefix + "bonds_day_inflow";
    }

    public String dailyTable() {
        return mysqlTablePrefix + "bonds_daily";
    }

    public String dailyPayoutTable() {
        return mysqlTablePrefix + "bonds_daily_payout";
    }

    public String couponLotsTable() {
        return mysqlTablePrefix + "bonds_coupon_lots";
    }

    public String notifyPendingTable() {
        return mysqlTablePrefix + "bonds_notify_pending";
    }

    public String governmentSettingsTable() {
        return mysqlTablePrefix + "bonds_government_settings";
    }

    public int goldChestSlots() {
        return goldChestRows * 9;
    }

    public int guiSize() {
        return 9 + goldChestSlots();
    }

    public int goldChestStartSlot() {
        return 9;
    }

    /** Human-readable claim window, e.g. {@code 28d} or {@code 48h}. */
    public static String formatClaimExpiry(int hours) {
        if (hours >= 24 && hours % 24 == 0) {
            return (hours / 24) + " days";
        }
        return hours + "h";
    }

    public String claimExpiryLabel() {
        return formatClaimExpiry(claimExpiryHours);
    }
}
