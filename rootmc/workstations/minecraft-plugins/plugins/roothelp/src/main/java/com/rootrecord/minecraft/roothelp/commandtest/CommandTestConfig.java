package com.rootrecord.minecraft.roothelp.commandtest;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record CommandTestConfig(
        boolean enabled,
        double totalGoldCap,
        int reminderIntervalSeconds,
        boolean actionBarReminders,
        boolean feeRefund,
        boolean mysqlEnabled,
        String jdbcUrl,
        String mysqlUsername,
        String mysqlPassword,
        String progressTable,
        String doneTable,
        Map<String, String> messages) {

    public boolean mysqlConfigured() {
        return mysqlEnabled && jdbcUrl != null && !jdbcUrl.isBlank();
    }

    public static CommandTestConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        CommandTestConfig base = fromSection(cfg);
        if (!needsMysqlFallback(base)) {
            return base;
        }
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        if (!db.enabled() || !db.isConfigured()) {
            return base;
        }
        String prefix = db.tablePrefix();
        return new CommandTestConfig(
                base.enabled(),
                base.totalGoldCap(),
                base.reminderIntervalSeconds(),
                base.actionBarReminders(),
                base.feeRefund(),
                true,
                db.jdbcUrl(),
                db.username(),
                db.password(),
                prefix + "command_test_progress",
                prefix + "command_test_done",
                base.messages());
    }

    private static CommandTestConfig fromSection(FileConfiguration cfg) {
        var section = cfg == null ? null : cfg.getConfigurationSection("command-test");
        // Final reset: pay-per-command-test is off unless explicitly enabled.
        boolean enabled = section != null && section.getBoolean("enabled", false);
        double cap = section == null ? 1000.0 : section.getDouble("total-gold-cap", 1000.0);
        int reminder = section == null ? 120 : section.getInt("reminder-interval-seconds", 120);
        boolean actionBar = section == null || section.getBoolean("action-bar-reminders", true);
        boolean feeRefund = section == null || section.getBoolean("fee-refund", true);

        String host = section == null ? "" : section.getString("mysql.host", "");
        int port = section == null ? 3306 : section.getInt("mysql.port", 3306);
        String database = section == null ? "" : section.getString("mysql.database", "");
        String username = section == null ? "" : section.getString("mysql.username", "");
        String password = section == null ? "" : section.getString("mysql.password", "");
        String prefix = section == null ? "root_" : section.getString("mysql.table-prefix", "root_");
        String jdbcParams = section == null ? "" : section.getString("mysql.jdbc-params", "");
        boolean mysqlEnabled = section != null
                && section.getBoolean("mysql.enabled", true)
                && host != null && !host.isBlank()
                && database != null && !database.isBlank();
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + (jdbcParams == null || jdbcParams.isBlank() ? "" : "?" + jdbcParams);

        Map<String, String> messages = new HashMap<>();
        if (section != null && section.isConfigurationSection("messages")) {
            for (String key : section.getConfigurationSection("messages").getKeys(false)) {
                messages.put(key, section.getString("messages." + key, ""));
            }
        }

        return new CommandTestConfig(
                enabled,
                Math.max(0, cap),
                Math.max(30, reminder),
                actionBar,
                feeRefund,
                mysqlEnabled,
                jdbcUrl,
                username == null ? "" : username,
                password == null ? "" : password,
                prefix + "command_test_progress",
                prefix + "command_test_done",
                Collections.unmodifiableMap(messages));
    }

    private static boolean needsMysqlFallback(CommandTestConfig cfg) {
        return cfg.mysqlEnabled() && (cfg.jdbcUrl() == null || cfg.jdbcUrl().isBlank()
                || cfg.mysqlUsername() == null || cfg.mysqlUsername().isBlank());
    }

    public String message(String key, String fallback) {
        String value = messages.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
