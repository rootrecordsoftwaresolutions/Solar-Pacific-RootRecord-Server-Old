package com.rootrecord.minecraft.rootwebstat;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** Snapshot of {@code plugins/RootMC/root-webstat.yml}. */
public final class WebstatConfig {

    private final boolean enabled;
    private final String bind;
    private final int port;
    private final String publicUrl;
    private final String token;
    private final String dayWorld;
    private final boolean onVanillaDay;
    private final int refreshSeconds;
    private final String economyTablePrefix;
    private final boolean includeZeroBalances;
    private final boolean pushEnabled;
    private final int pushIntervalSeconds;
    private final String serverNameOverride;

    private final boolean featureHttpSite;
    private final boolean featureStatsApi;
    private final boolean featureDataApi;
    private final boolean featureHealthApi;
    private final boolean featureStatsDayRecompute;
    private final boolean featureCloudPush;
    private final boolean featureLogsPage;
    private final boolean featureLogsTail;
    private final boolean featureLogsDownload;
    private final int logsMaxTailLines;
    private final int logsMaxDownloadBytes;

    public WebstatConfig(
            boolean enabled,
            String bind,
            int port,
            String publicUrl,
            String token,
            String dayWorld,
            boolean onVanillaDay,
            int refreshSeconds,
            String economyTablePrefix,
            boolean includeZeroBalances,
            boolean pushEnabled,
            int pushIntervalSeconds,
            String serverNameOverride,
            boolean featureHttpSite,
            boolean featureStatsApi,
            boolean featureDataApi,
            boolean featureHealthApi,
            boolean featureStatsDayRecompute,
            boolean featureCloudPush,
            boolean featureLogsPage,
            boolean featureLogsTail,
            boolean featureLogsDownload,
            int logsMaxTailLines,
            int logsMaxDownloadBytes) {
        this.enabled = enabled;
        this.bind = bind;
        this.port = port;
        this.publicUrl = publicUrl;
        this.token = token;
        this.dayWorld = dayWorld;
        this.onVanillaDay = onVanillaDay;
        this.refreshSeconds = refreshSeconds;
        this.economyTablePrefix = economyTablePrefix;
        this.includeZeroBalances = includeZeroBalances;
        this.pushEnabled = pushEnabled;
        this.pushIntervalSeconds = pushIntervalSeconds;
        this.serverNameOverride = serverNameOverride;
        this.featureHttpSite = featureHttpSite;
        this.featureStatsApi = featureStatsApi;
        this.featureDataApi = featureDataApi;
        this.featureHealthApi = featureHealthApi;
        this.featureStatsDayRecompute = featureStatsDayRecompute;
        this.featureCloudPush = featureCloudPush;
        this.featureLogsPage = featureLogsPage;
        this.featureLogsTail = featureLogsTail;
        this.featureLogsDownload = featureLogsDownload;
        this.logsMaxTailLines = logsMaxTailLines;
        this.logsMaxDownloadBytes = logsMaxDownloadBytes;
    }

    public static WebstatConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        if (cfg == null) {
            cfg = new YamlConfiguration();
        }
        String prefix = cfg.getString("stats.economy-table-prefix", "");
        if (prefix == null || prefix.isBlank()) {
            prefix = resolveEssentialsPrefix(plugin);
        }
        int port = cfg.getInt("web.port", 8765);
        if (port < 1 || port > 65535) {
            port = 8765;
        }
        int maxTail = Math.max(1, Math.min(2000, cfg.getInt("logs.max-tail-lines", 500)));
        int maxBytes = Math.max(4096, Math.min(16 * 1024 * 1024, cfg.getInt("logs.max-download-bytes", 2_097_152)));
        return new WebstatConfig(
                cfg.getBoolean("enabled", true),
                str(cfg.getString("web.bind"), "0.0.0.0"),
                port,
                str(cfg.getString("web.public-url"), ""),
                str(cfg.getString("web.token"), ""),
                str(cfg.getString("stats.day-world"), ""),
                cfg.getBoolean("stats.on-vanilla-day", true),
                Math.max(0, cfg.getInt("stats.refresh-seconds", 0)),
                prefix,
                cfg.getBoolean("stats.include-zero-balances", false),
                cfg.getBoolean("cloud.push-enabled", true),
                Math.max(0, cfg.getInt("cloud.push-interval-seconds", 0)),
                str(cfg.getString("server-name"), ""),
                cfg.getBoolean("features.http-site", true),
                cfg.getBoolean("features.stats-api", true),
                cfg.getBoolean("features.data-api", true),
                cfg.getBoolean("features.health-api", true),
                cfg.getBoolean("features.stats-day-recompute", true),
                cfg.getBoolean("features.cloud-push", true),
                cfg.getBoolean("features.logs-page", true),
                cfg.getBoolean("features.logs-tail", true),
                cfg.getBoolean("features.logs-download", true),
                maxTail,
                maxBytes);
    }

    private static String resolveEssentialsPrefix(JavaPlugin plugin) {
        try {
            RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, null);
            if (db != null) {
                String p = db.tablePrefix();
                if (p != null && !p.isBlank()) {
                    return p.trim();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        File file = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_ESSENTIALS_CONFIG);
        if (file.isFile()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            String p = yml.getString("mysql.table-prefix", yml.getString("table-prefix", ""));
            if (p != null && !p.isBlank()) {
                return p.trim();
            }
        }
        return "root_";
    }

    private static String str(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    public boolean enabled() {
        return enabled;
    }

    public String bind() {
        return bind;
    }

    public int port() {
        return port;
    }

    public String publicUrl() {
        return publicUrl;
    }

    public String token() {
        return token;
    }

    public String dayWorld() {
        return dayWorld;
    }

    public boolean onVanillaDay() {
        return onVanillaDay && featureStatsDayRecompute;
    }

    public int refreshSeconds() {
        return refreshSeconds;
    }

    public String economyTablePrefix() {
        return economyTablePrefix;
    }

    public boolean includeZeroBalances() {
        return includeZeroBalances;
    }

    /** Cloud push runs only when both legacy cloud.push-enabled and features.cloud-push are on. */
    public boolean pushEnabled() {
        return pushEnabled && featureCloudPush;
    }

    public int pushIntervalSeconds() {
        return pushIntervalSeconds;
    }

    public String serverNameOverride() {
        return serverNameOverride;
    }

    public boolean featureHttpSite() {
        return featureHttpSite;
    }

    public boolean featureStatsApi() {
        return featureStatsApi;
    }

    public boolean featureDataApi() {
        return featureDataApi;
    }

    public boolean featureHealthApi() {
        return featureHealthApi;
    }

    public boolean featureStatsDayRecompute() {
        return featureStatsDayRecompute;
    }

    public boolean featureCloudPush() {
        return featureCloudPush;
    }

    public boolean featureLogsPage() {
        return featureLogsPage;
    }

    public boolean featureLogsTail() {
        return featureLogsTail;
    }

    public boolean featureLogsDownload() {
        return featureLogsDownload;
    }

    public int logsMaxTailLines() {
        return logsMaxTailLines;
    }

    public int logsMaxDownloadBytes() {
        return logsMaxDownloadBytes;
    }

    public boolean anyHttpSurface() {
        return featureHttpSite
                || featureStatsApi
                || featureDataApi
                || featureHealthApi
                || featureLogsPage
                || featureLogsTail
                || featureLogsDownload;
    }

    public String economyBalancesTable() {
        return economyTablePrefix + "economy_balances";
    }

    public String featuresJson() {
        return "{\"http_site\":"
                + featureHttpSite
                + ",\"stats_api\":"
                + featureStatsApi
                + ",\"data_api\":"
                + featureDataApi
                + ",\"health_api\":"
                + featureHealthApi
                + ",\"stats_day_recompute\":"
                + featureStatsDayRecompute
                + ",\"cloud_push\":"
                + featureCloudPush
                + ",\"logs_page\":"
                + featureLogsPage
                + ",\"logs_tail\":"
                + featureLogsTail
                + ",\"logs_download\":"
                + featureLogsDownload
                + "}";
    }
}
