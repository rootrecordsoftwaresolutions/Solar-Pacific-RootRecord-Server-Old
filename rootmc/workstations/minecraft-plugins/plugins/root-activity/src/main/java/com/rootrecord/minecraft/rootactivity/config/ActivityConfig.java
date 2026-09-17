package com.rootrecord.minecraft.rootactivity.config;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record ActivityConfig(
        boolean enabled,
        int flushIntervalSeconds,
        int peakHourCount,
        boolean ipLookupEnabled,
        String ipLookupUrlTemplate,
        int ipCacheHours,
        boolean mysqlEnabled,
        String jdbcUrl,
        String mysqlUsername,
        String mysqlPassword,
        String tablePrefix,
        String timezoneTable,
        String hourlyTable,
        String prefix,
        Map<String, String> messages) {

    public static ActivityConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        if (cfg == null) {
            return defaults();
        }
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        boolean mysqlEnabled = db.enabled() && db.isConfigured();

        Map<String, String> messages = new HashMap<>();
        if (cfg.isConfigurationSection("messages")) {
            for (String key : cfg.getConfigurationSection("messages").getKeys(false)) {
                messages.put(key, cfg.getString("messages." + key, ""));
            }
        }

        return new ActivityConfig(
                cfg.getBoolean("enabled", true),
                Math.max(15, cfg.getInt("flush-interval-seconds", 60)),
                Math.max(1, cfg.getInt("peak-hour-count", 3)),
                cfg.getBoolean("ip-lookup.enabled", true),
                cfg.getString(
                        "ip-lookup.url-template",
                        "http://ip-api.com/json/{ip}?fields=status,offset,timezone"),
                Math.max(1, cfg.getInt("ip-lookup.cache-hours", 24)),
                mysqlEnabled,
                mysqlEnabled ? db.jdbcUrl() : "",
                db.username(),
                db.password(),
                db.tablePrefix(),
                db.tablePrefix() + "activity_timezone",
                db.tablePrefix() + "activity_hourly",
                cfg.getString("messages.prefix", ""),
                Collections.unmodifiableMap(messages));
    }

    public boolean mysqlConfigured() {
        return mysqlEnabled && jdbcUrl != null && !jdbcUrl.isBlank();
    }

    private static ActivityConfig defaults() {
        return new ActivityConfig(
                true,
                60,
                3,
                true,
                "http://ip-api.com/json/{ip}?fields=status,offset,timezone",
                24,
                false,
                "",
                "",
                "",
                "root_",
                "root_activity_timezone",
                "root_activity_hourly",
                "",
                Map.of());
    }
}
