package com.rootrecord.minecraft.rootrewards.config;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public record RewardsConfig(
        boolean mysqlEnabled,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String mysqlTablePrefix,
        String mysqlJdbcParams,
        int playtimeCheckIntervalSeconds,
        boolean useRootMcPlaytime,
        String rootmcPlaytimeTable,
        int playtimeTopLimit,
        int voteGoldMin,
        int voteGoldMax,
        int voteBroadcastEvery,
        boolean voteBroadcastEnabled,
        boolean voteDiscordRelay,
        boolean voteReminderEnabled,
        int voteReminderIntervalSeconds,
        int voteReminderAfterHours,
        boolean voteNetworkListener,
        boolean voteMirrorToClaims,
        String voteMirrorClaimsServerId,
        int voteAppreciationTokens,
        List<VoteLink> voteLinks) {

    public record VoteLink(
            String name,
            String url,
            ResetMode resetMode,
            int rollingHours,
            LocalTime dailyAt,
            ZoneId timezone) {

        public enum ResetMode {
            ROLLING,
            DAILY
        }
    }

    public int rollVoteGold() {
        if (voteGoldMax <= voteGoldMin) {
            return voteGoldMin;
        }
        return ThreadLocalRandom.current().nextInt(voteGoldMin, voteGoldMax + 1);
    }

    public static RewardsConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        List<VoteLink> links = new ArrayList<>();
        if (cfg.isList("vote.links")) {
            for (var raw : cfg.getMapList("vote.links")) {
                Object nameObj = raw.get("name");
                Object urlObj = raw.get("url");
                String name = nameObj == null ? "" : String.valueOf(nameObj).trim();
                String url = urlObj == null ? "" : String.valueOf(urlObj).trim();
                if (!name.isEmpty() && !url.isEmpty()) {
                    links.add(parseVoteLink(name, url, raw));
                }
            }
        }
        int goldMin = Math.max(1, cfg.getInt("vote.gold-min", 1));
        int legacyGold = (int) Math.max(1, Math.rint(cfg.getDouble("vote.gold", 20)));
        int goldMax = Math.max(goldMin, cfg.getInt("vote.gold-max", legacyGold));
        int broadcastEvery = Math.max(1, cfg.getInt("vote.broadcast-every", 5));
        boolean broadcastEnabled = cfg.getBoolean("vote.broadcast-enabled", true);
        boolean discordRelay = cfg.getBoolean("vote.discord-relay", true);
        boolean reminderEnabled = cfg.getBoolean("vote.reminder-enabled", true);
        int reminderInterval = Math.max(300, cfg.getInt("vote.reminder-interval-seconds", 3600));
        int reminderAfterHours = Math.max(1, cfg.getInt("vote.reminder-after-hours", 24));
        boolean networkListener = cfg.getBoolean("vote.network-listener", false);
        boolean mirrorToClaims = cfg.getBoolean("vote.mirror-to-claims", false);
        String mirrorClaimsId = cfg.getString(
                "vote.mirror-claims-server-id",
                "4963895e-0964-48b8-81b7-1f40a966e8be");
        if (mirrorClaimsId == null || mirrorClaimsId.isBlank()) {
            mirrorClaimsId = "4963895e-0964-48b8-81b7-1f40a966e8be";
        }
        int appreciationTokens = Math.max(0, cfg.getInt("vote.appreciation-tokens", 1));
        return new RewardsConfig(
                db.enabled(),
                db.host(),
                db.port(),
                db.database(),
                db.username(),
                db.password(),
                db.tablePrefix(),
                db.jdbcParams(),
                Math.max(30, cfg.getInt("playtime.check-interval-seconds", 60)),
                cfg.getBoolean("playtime.use-rootmc-playtime", true),
                cfg.getString("playtime.rootmc-playtime-table", "rootmc_playtime"),
                Math.max(1, Math.min(50, cfg.getInt("playtime.top-limit", 10))),
                goldMin,
                goldMax,
                broadcastEvery,
                broadcastEnabled,
                discordRelay,
                reminderEnabled,
                reminderInterval,
                reminderAfterHours,
                networkListener,
                mirrorToClaims,
                mirrorClaimsId.trim(),
                appreciationTokens,
                List.copyOf(links));
    }

    @SuppressWarnings("rawtypes")
    private static VoteLink parseVoteLink(String name, String url, java.util.Map raw) {
        String resetRaw = stringOrEmpty(raw.get("reset")).toLowerCase(Locale.ROOT);
        VoteLink.ResetMode mode =
                "daily".equals(resetRaw) ? VoteLink.ResetMode.DAILY : VoteLink.ResetMode.ROLLING;
        int hours = 24;
        Object hoursObj = raw.get("hours");
        if (hoursObj instanceof Number number) {
            hours = Math.max(1, number.intValue());
        } else if (hoursObj != null) {
            try {
                hours = Math.max(1, Integer.parseInt(String.valueOf(hoursObj).trim()));
            } catch (NumberFormatException ignored) {
                hours = 24;
            }
        }
        LocalTime at = LocalTime.MIDNIGHT;
        String atRaw = stringOrEmpty(raw.get("at"));
        if (!atRaw.isEmpty()) {
            try {
                at = LocalTime.parse(atRaw);
            } catch (Exception ignored) {
                at = LocalTime.MIDNIGHT;
            }
        }
        ZoneId zone = ZoneId.of("UTC");
        String tzRaw = stringOrEmpty(raw.get("timezone"));
        if (!tzRaw.isEmpty()) {
            try {
                zone = ZoneId.of(tzRaw);
            } catch (Exception ignored) {
                zone = ZoneId.of("UTC");
            }
        } else if (mode == VoteLink.ResetMode.DAILY) {
            zone = ZoneId.of("America/New_York");
        }
        return new VoteLink(name, url, mode, hours, at, zone);
    }

    private static String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public String playtimeTableFqn() {
        return mysqlTablePrefix + rootmcPlaytimeTable;
    }

    public String claimsTable() {
        return mysqlTablePrefix + "rewards_claims";
    }

    public String fallbackPlaytimeTable() {
        return mysqlTablePrefix + "rewards_playtime";
    }

    public String votesTable() {
        return mysqlTablePrefix + "rewards_votes";
    }
}
