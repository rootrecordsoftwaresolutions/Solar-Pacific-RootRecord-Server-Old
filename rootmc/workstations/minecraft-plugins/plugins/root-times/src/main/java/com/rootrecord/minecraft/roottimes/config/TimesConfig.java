package com.rootrecord.minecraft.roottimes.config;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class TimesConfig {

    private final boolean dayEnabled;
    private final String dayTimezone;
    private final int lengthMinutes;
    private final int middayMinute;
    private final int midnightMinute;
    private final String dayWorld;
    private final boolean harvestPlaytime;
    private final boolean harvestActivity;
    private final int flushIntervalSeconds;
    private final String defaultTimezoneKey;
    private final boolean ipLookupEnabled;
    private final String ipLookupUrlTemplate;
    private final int ipCacheHours;
    private final boolean afkEnabled;
    private final int afkAfterSeconds;
    private final boolean afkExcludePlaytime;
    private final boolean afkExcludeActivity;
    private final boolean afkBroadcast;
    private final boolean welcomeEnabled;
    private final int welcomeDelayTicks;
    private final String welcomeLinePlaytime;
    private final String welcomeLineMcDay;
    private final boolean webEnabled;
    private final String webHost;
    private final int webPort;
    private final String webToken;
    private final boolean mysqlStatusEnabled;
    private final int mysqlStatusIntervalSeconds;
    private final String tablePrefix;
    private final String playtimeTable;
    private final String playtimeMonthlyTable;
    private final String serverId;
    private final String activityTimezoneTable;
    private final String activityHourlyTable;
    private final String afkSessionsTable;
    private final String timesStatusTable;
    private final RootMcDatabaseConfig.DatabaseSettings database;
    private final JavaPlugin plugin;
    private final String dayIdBaseRaw;

    private TimesConfig(
            boolean dayEnabled,
            String dayTimezone,
            int lengthMinutes,
            int middayMinute,
            int midnightMinute,
            String dayWorld,
            boolean harvestPlaytime,
            boolean harvestActivity,
            int flushIntervalSeconds,
            String defaultTimezoneKey,
            boolean ipLookupEnabled,
            String ipLookupUrlTemplate,
            int ipCacheHours,
            boolean afkEnabled,
            int afkAfterSeconds,
            boolean afkExcludePlaytime,
            boolean afkExcludeActivity,
            boolean afkBroadcast,
            boolean welcomeEnabled,
            int welcomeDelayTicks,
            String welcomeLinePlaytime,
            String welcomeLineMcDay,
            boolean webEnabled,
            String webHost,
            int webPort,
            String webToken,
            boolean mysqlStatusEnabled,
            int mysqlStatusIntervalSeconds,
            String tablePrefix,
            String playtimeTable,
            String playtimeMonthlyTable,
            String serverId,
            String activityTimezoneTable,
            String activityHourlyTable,
            String afkSessionsTable,
            String timesStatusTable,
            RootMcDatabaseConfig.DatabaseSettings database,
            JavaPlugin plugin,
            String dayIdBaseRaw) {
        this.dayEnabled = dayEnabled;
        this.dayTimezone = dayTimezone;
        this.lengthMinutes = lengthMinutes;
        this.middayMinute = middayMinute;
        this.midnightMinute = midnightMinute;
        this.dayWorld = dayWorld;
        this.harvestPlaytime = harvestPlaytime;
        this.harvestActivity = harvestActivity;
        this.flushIntervalSeconds = flushIntervalSeconds;
        this.defaultTimezoneKey = defaultTimezoneKey;
        this.ipLookupEnabled = ipLookupEnabled;
        this.ipLookupUrlTemplate = ipLookupUrlTemplate;
        this.ipCacheHours = ipCacheHours;
        this.afkEnabled = afkEnabled;
        this.afkAfterSeconds = afkAfterSeconds;
        this.afkExcludePlaytime = afkExcludePlaytime;
        this.afkExcludeActivity = afkExcludeActivity;
        this.afkBroadcast = afkBroadcast;
        this.welcomeEnabled = welcomeEnabled;
        this.welcomeDelayTicks = welcomeDelayTicks;
        this.welcomeLinePlaytime = welcomeLinePlaytime;
        this.welcomeLineMcDay = welcomeLineMcDay;
        this.webEnabled = webEnabled;
        this.webHost = webHost;
        this.webPort = webPort;
        this.webToken = webToken;
        this.mysqlStatusEnabled = mysqlStatusEnabled;
        this.mysqlStatusIntervalSeconds = mysqlStatusIntervalSeconds;
        this.tablePrefix = tablePrefix;
        this.playtimeTable = playtimeTable;
        this.playtimeMonthlyTable = playtimeMonthlyTable;
        this.serverId = serverId;
        this.activityTimezoneTable = activityTimezoneTable;
        this.activityHourlyTable = activityHourlyTable;
        this.afkSessionsTable = afkSessionsTable;
        this.timesStatusTable = timesStatusTable;
        this.database = database;
        this.plugin = plugin;
        this.dayIdBaseRaw = dayIdBaseRaw == null ? "0" : dayIdBaseRaw;
    }

    public static TimesConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        String prefix = db.tablePrefix() == null || db.tablePrefix().isBlank() ? "root_" : db.tablePrefix();
        String play = cfg.getString("tables.playtime", "playtime");
        String playMonthly = cfg.getString("tables.playtime-monthly", "playtime_monthly");
        // Prefer server-bucket (towny|claims) so Official sync + /playtime share stable labels.
        String bucket = cfg.getString("playtime.server-bucket", "").trim();
        String configuredServerId = cfg.getString("playtime.server-id", "").trim();
        String cloudServerId = RootRecordCloudConfig.resolve(plugin, cfg).serverId();
        String rawId = !bucket.isBlank()
                ? bucket
                : (!configuredServerId.isBlank()
                        ? configuredServerId
                        : (!cloudServerId.isBlank() ? cloudServerId : "local"));
        String serverId = normalizeServerBucket(rawId);
        String tz = cfg.getString("tables.activity-timezone", "activity_timezone");
        String hourly = cfg.getString("tables.activity-hourly", "activity_hourly");
        String afk = cfg.getString("tables.afk-sessions", "times_afk_sessions");
        String timesStatus = cfg.getString("tables.times-status", "times_status");
        boolean mysqlStatus = cfg.getBoolean("mysql-status.enabled", true);
        int statusInterval = cfg.contains("mysql-status.interval-seconds")
                ? cfg.getInt("mysql-status.interval-seconds", 5)
                : cfg.getInt("cloud-status.interval-seconds", 5);
        return new TimesConfig(
                cfg.getBoolean("minecraft-day.enabled", true),
                cfg.getString("minecraft-day.timezone", "UTC"),
                Math.max(1, cfg.getInt("minecraft-day.length-minutes", 20)),
                cfg.getInt("minecraft-day.midday-minute", 0),
                cfg.getInt("minecraft-day.midnight-minute", 15),
                cfg.getString("minecraft-day.world", ""),
                cfg.getBoolean("harvest.playtime", true),
                cfg.getBoolean("harvest.activity", true),
                Math.max(15, cfg.getInt("harvest.flush-interval-seconds", 60)),
                cfg.getString("harvest.default-timezone-key", "utc_plus_0"),
                cfg.getBoolean("ip-lookup.enabled", true),
                cfg.getString(
                        "ip-lookup.url-template",
                        "http://ip-api.com/json/{ip}?fields=status,offset,timezone"),
                Math.max(1, cfg.getInt("ip-lookup.cache-hours", 24)),
                cfg.getBoolean("afk.enabled", true),
                Math.max(30, cfg.getInt("afk.after-seconds", 300)),
                cfg.getBoolean("afk.exclude-from-playtime", true),
                cfg.getBoolean("afk.exclude-from-activity", true),
                cfg.getBoolean("afk.broadcast", true),
                cfg.getBoolean("welcome.enabled", true),
                Math.max(1, cfg.getInt("welcome.delay-ticks", 40)),
                cfg.getString(
                        "welcome.line-playtime",
                        "&7Total Playtime: &f{playtime} &7- Your Current Time: &f{time} {timezone}"),
                cfg.getString(
                        "welcome.line-mc-day",
                        "&7Minecraft day &f#{worldDay} &7- Ingame time: &f{todTicks} &7({phase})"),
                cfg.getBoolean("web.enabled", true),
                cfg.getString("web.host", "127.0.0.1"),
                Math.max(1, cfg.getInt("web.port", 8765)),
                cfg.getString("web.token", ""),
                mysqlStatus,
                Math.max(5, statusInterval),
                prefix,
                prefix + play,
                prefix + playMonthly,
                serverId,
                prefix + tz,
                prefix + hourly,
                prefix + afk,
                prefix + timesStatus,
                db,
                plugin,
                cfg.getString("minecraft-day.day-id-base", "0"));
    }

    public boolean dayEnabled() {
        return dayEnabled;
    }

    public String dayTimezone() {
        return dayTimezone;
    }

    public int lengthMinutes() {
        return lengthMinutes;
    }

    public int middayMinute() {
        return middayMinute;
    }

    public int midnightMinute() {
        return midnightMinute;
    }

    public String dayWorld() {
        return dayWorld;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    /** Raw {@code minecraft-day.day-id-base} ({@code 0} / {@code auto} / absolute id). */
    public String dayIdBaseRaw() {
        return dayIdBaseRaw;
    }

    public boolean harvestPlaytime() {
        return harvestPlaytime;
    }

    public boolean harvestActivity() {
        return harvestActivity;
    }

    public int flushIntervalSeconds() {
        return flushIntervalSeconds;
    }

    public String defaultTimezoneKey() {
        return defaultTimezoneKey;
    }

    public boolean ipLookupEnabled() {
        return ipLookupEnabled;
    }

    public String ipLookupUrlTemplate() {
        return ipLookupUrlTemplate;
    }

    public int ipCacheHours() {
        return ipCacheHours;
    }

    public boolean afkEnabled() {
        return afkEnabled;
    }

    public int afkAfterSeconds() {
        return afkAfterSeconds;
    }

    public boolean afkExcludePlaytime() {
        return afkExcludePlaytime;
    }

    public boolean afkExcludeActivity() {
        return afkExcludeActivity;
    }

    public boolean afkBroadcast() {
        return afkBroadcast;
    }

    public boolean welcomeEnabled() {
        return welcomeEnabled;
    }

    public int welcomeDelayTicks() {
        return welcomeDelayTicks;
    }

    public String welcomeLinePlaytime() {
        return welcomeLinePlaytime;
    }

    public String welcomeLineMcDay() {
        return welcomeLineMcDay;
    }

    public boolean webEnabled() {
        return webEnabled;
    }

    public String webHost() {
        return webHost;
    }

    public int webPort() {
        return webPort;
    }

    public String webToken() {
        return webToken;
    }

    public boolean mysqlStatusEnabled() {
        return mysqlStatusEnabled;
    }

    public int mysqlStatusIntervalSeconds() {
        return mysqlStatusIntervalSeconds;
    }

    /** @deprecated HTTPS cloud push retired — use {@link #mysqlStatusEnabled()}. */
    @Deprecated
    public boolean cloudStatusEnabled() {
        return false;
    }

    /** @deprecated HTTPS cloud push retired — use {@link #mysqlStatusIntervalSeconds()}. */
    @Deprecated
    public int cloudStatusIntervalSeconds() {
        return mysqlStatusIntervalSeconds;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public String playtimeTable() {
        return playtimeTable;
    }

    public String playtimeMonthlyTable() {
        return playtimeMonthlyTable;
    }

    /** Cloud / configured host id used as per-server playtime scope. */
    public String serverId() {
        return serverId;
    }

    /** Canonical playtime scopes: {@code towny}, {@code claims}, or a stable lowercase id. */
    public static String normalizeServerBucket(String raw) {
        if (raw == null || raw.isBlank()) {
            return "local";
        }
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "claims", "c", "g2", "gen2", "gen-2" -> "claims";
            case "towny", "t", "g1", "gen1", "gen-1", "official" -> "towny";
            case "dev", "test", "portal", "devportal", "rootmc-dev", "rootmc_dev" -> "dev";
            default -> s;
        };
    }

    public String activityTimezoneTable() {
        return activityTimezoneTable;
    }

    public String activityHourlyTable() {
        return activityHourlyTable;
    }

    public String afkSessionsTable() {
        return afkSessionsTable;
    }

    public String timesStatusTable() {
        return timesStatusTable;
    }

    public RootMcDatabaseConfig.DatabaseSettings database() {
        return database;
    }

    public boolean mysqlReady() {
        return database != null && database.enabled() && database.isConfigured();
    }
}
