package com.rootrecord.minecraft.rootupkeep.config;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.ZoneId;

public record UpkeepConfig(
        boolean enabled,
        boolean mysqlEnabled,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String mysqlTablePrefix,
        String mysqlJdbcParams,
        String playtimeTable,
        int scheduleHourHst,
        int pollSeconds,
        int graceDays,
        double week1RatePercent,
        double week2RatePercent,
        double week3RatePercent,
        double week4RatePercent,
        double minBalance,
        double minTax,
        String playerTaxMessage,
        String townTaxMessage,
        String nationTaxMessage,
        String runCompleteMessage,
        String reloadDoneMessage,
        String noPermissionMessage,
        String returnSummaryHeader,
        String returnSummaryPlayer,
        String returnSummaryTown,
        String returnSummaryNation,
        String returnSummaryTotal) {

    public static final ZoneId HST = ZoneId.of("Pacific/Honolulu");

    public static UpkeepConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        String prefix = db.tablePrefix();
        String playtimeRaw = cfg.getString("mysql.playtime-table", "rootmc_playtime").trim();
        String playtimeTable = playtimeRaw.startsWith(prefix) ? playtimeRaw : prefix + playtimeRaw;
        return new UpkeepConfig(
                cfg.getBoolean("enabled", true),
                db.enabled(),
                db.host(),
                db.port(),
                db.database(),
                db.username(),
                db.password(),
                prefix,
                db.jdbcParams(),
                playtimeTable,
                Math.max(0, Math.min(23, cfg.getInt("schedule.hour-hst", 0))),
                Math.max(15, cfg.getInt("schedule.poll-seconds", 30)),
                Math.max(1, cfg.getInt("inactivity.grace-days", 30)),
                cfg.getDouble("inactivity.week-1-rate", 0.01),
                cfg.getDouble("inactivity.week-2-rate", 0.05),
                cfg.getDouble("inactivity.week-3-rate", 0.1),
                cfg.getDouble("inactivity.week-4-rate", 1.0),
                Math.max(0, cfg.getDouble("inactivity.min-balance", 0.01)),
                Math.max(0, cfg.getDouble("inactivity.min-tax", 0.01)),
                cfg.getString(
                        "messages.player-tax",
                        "&7Inactivity tax: &f{target} &7charged &b{amount} G &7(&b{rate}%&7, &f{days} &7days idle)"),
                cfg.getString(
                        "messages.town-tax",
                        "&7Town inactivity tax: &f{target} &7charged &b{amount} G &7(&b{rate}%&7, &f{days} &7days since last member login)"),
                cfg.getString(
                        "messages.nation-tax",
                        "&7Nation inactivity tax: &f{target} &7charged &b{amount} G &7(&b{rate}%&7, &f{days} &7days since last member login)"),
                cfg.getString(
                        "messages.run-complete",
                        "&7Inactivity tax cycle complete — &f{count} &7debit(s) applied."),
                cfg.getString("messages.reload-done", "&aRoot-Upkeep reloaded."),
                cfg.getString("messages.no-permission", "&cYou do not have permission."),
                cfg.getString(
                        "messages.return-summary-header",
                        "&6&lWelcome back &7— you were away &f{days} &7days. Inactivity tax while you were gone:"),
                cfg.getString(
                        "messages.return-summary-player",
                        "&8  &7Your wallet: &c-{amount} G"),
                cfg.getString(
                        "messages.return-summary-town",
                        "&8  &7Town &f{town}&7 bank: &c-{amount} G"),
                cfg.getString(
                        "messages.return-summary-nation",
                        "&8  &7Nation &f{nation}&7 bank: &c-{amount} G"),
                cfg.getString(
                        "messages.return-summary-total",
                        "&7Total lost to inactivity tax: &c-{amount} G"));
    }

    public String jdbcUrl() {
        String params = mysqlJdbcParams == null || mysqlJdbcParams.isBlank() ? "" : "?" + mysqlJdbcParams;
        return "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + params;
    }

    public boolean mysqlConfigured() {
        return mysqlEnabled
                && mysqlHost != null
                && !mysqlHost.isBlank()
                && mysqlDatabase != null
                && !mysqlDatabase.isBlank()
                && mysqlUsername != null
                && !mysqlUsername.isBlank();
    }
}
