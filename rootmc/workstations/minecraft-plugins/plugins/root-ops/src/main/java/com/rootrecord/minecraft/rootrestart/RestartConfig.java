package com.rootrecord.minecraft.rootrestart;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;

public record RestartConfig(
        boolean dailyEnabled,
        ZoneId dailyZone,
        boolean discordRelay,
        boolean discordStartupNotify,
        String prefix,
        String countdownMsg,
        String dailyCountdownMsg,
        String startedManualMsg,
        String cancelledMsg,
        String alreadyRunningMsg,
        String restartingNowMsg,
        String startupCompleteMsg,
        String stopPrefix,
        String stopCountdownMsg,
        String startedStopMsg,
        String stoppingNowMsg,
        String updatePrefix,
        String updateCountdownMsg,
        String startedUpdateMsg) {

    private static final int[] MANUAL_WARN_SECONDS = {60, 30, 15, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    private static final int[] DAILY_WARN_SECONDS = {300, 180, 60, 30, 15, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

    public static RestartConfig from(FileConfiguration cfg) {
        String zoneId = cfg.getString("daily.zone", "Pacific/Honolulu").trim();
        ZoneId zone;
        try {
            zone = ZoneId.of(zoneId);
        } catch (Exception ex) {
            zone = ZoneId.of("Pacific/Honolulu");
        }
        return new RestartConfig(
                cfg.getBoolean("daily.enabled", true),
                zone,
                cfg.getBoolean("discord.relay", true),
                cfg.getBoolean("discord.startup-notify", true),
                cfg.getString("messages.prefix", "&c&l[Restart] &r"),
                cfg.getString("messages.countdown", "&eServer restarting in &f{time}&e."),
                cfg.getString("messages.daily-countdown", "&eMorning restart in &f{time}&e."),
                cfg.getString("messages.started-manual", "&7Manual restart started by &f{player}&7."),
                cfg.getString("messages.cancelled", "&aCountdown cancelled."),
                cfg.getString("messages.already-running", "&cA countdown is already running."),
                cfg.getString("messages.restarting-now", "&c&lRestarting now!"),
                cfg.getString("messages.startup-complete", "&aServer is online - &fplay.rootmc.net"),
                cfg.getString("messages.stop-prefix", "&c[Update] &r"),
                cfg.getString(
                        "messages.stop-countdown",
                        "&eServer stopping for an update in &f{time}&e."),
                cfg.getString(
                        "messages.started-stop",
                        "&7Stop for update started by &f{player}&7."),
                cfg.getString("messages.stopping-now", "&c&lStopping for update!"),
                cfg.getString("messages.update-prefix", "&c[Plugins] &r"),
                cfg.getString(
                        "messages.update-countdown",
                        "&ePlugin updates ready — restarting in &f{time}&e."),
                cfg.getString(
                        "messages.started-update",
                        "&7Plugin update restart started (&f{summary}&7)."));
    }

    public static Set<Integer> warningSeconds(RestartKind kind) {
        int[] raw =
                kind == RestartKind.DAILY || kind == RestartKind.MORNING
                        ? DAILY_WARN_SECONDS
                        : MANUAL_WARN_SECONDS;
        TreeSet<Integer> set = new TreeSet<>();
        for (int s : raw) {
            set.add(s);
        }
        return set;
    }

    public static int totalSeconds(RestartKind kind) {
        return kind == RestartKind.DAILY || kind == RestartKind.MORNING ? 300 : 60;
    }
}
