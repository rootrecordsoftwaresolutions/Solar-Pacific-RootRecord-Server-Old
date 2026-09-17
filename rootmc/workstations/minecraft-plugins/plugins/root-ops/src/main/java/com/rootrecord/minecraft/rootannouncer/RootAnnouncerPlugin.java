package com.rootrecord.minecraft.rootannouncer;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootannouncer.cloud.SeasonCloudClient;
import com.rootrecord.minecraft.rootannouncer.cloud.SeasonSyncTask;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.common.RootMcPublicReachout;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.logging.Logger;

public final class RootAnnouncerPlugin implements SeasonSyncTask.RootAnnouncerPluginRef {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootAnnouncerPlugin(org.bukkit.plugin.java.JavaPlugin host) {
        this.host = host;
    }

    public org.bukkit.plugin.java.JavaPlugin host() { return host; }
    public org.bukkit.plugin.Plugin getPlugin() { return host; }
    public java.util.logging.Logger getLogger() { return host.getLogger(); }
    public org.bukkit.Server getServer() { return host.getServer(); }
    public java.io.File getDataFolder() { return host.getDataFolder(); }
    public org.bukkit.command.PluginCommand getCommand(String name) { return host.getCommand(name); }
    public org.bukkit.plugin.PluginDescriptionFile getDescription() { return host.getDescription(); }
    public java.io.InputStream getResource(String path) { return host.getResource(path); }
    public void saveResource(String path, boolean replace) { host.saveResource(path, replace); }
    public org.bukkit.scheduler.BukkitScheduler getScheduler() { return host.getServer().getScheduler(); }
    public String getName() { return host.getName(); }

    private static final ZoneId HST = ZoneId.of("Pacific/Honolulu");

    private RootRecordYamlConfig yamlConfig;
    private AnnouncerConfig announcerConfig;
    private AnnouncerService announcer;
    private AnnouncerToggleStore toggles;
    private BukkitTask announceTask;
    private SeasonSyncTask seasonSync;
    private volatile List<String> seasonLines = List.of();

    public void enable() {
        RootRecordFolders.ensureDir(host);
        RootRecordCloudConfig.ensureDefaults(host);
        yamlConfig = new RootRecordYamlConfig(host, RootRecordFolders.ROOT_ANNOUNCER_CONFIG, "root-announcer.yml");
        yamlConfig.load();
        reloadLocalConfig();

        var adminCmd = getCommand("rootannouncer");
        if (adminCmd != null) {
            RootAnnouncerCommand handler = new RootAnnouncerCommand(this);
            adminCmd.setExecutor(handler);
            adminCmd.setTabCompleter(handler);
        }
        var toggleCmd = getCommand("announcer");
        if (toggleCmd != null) {
            AnnouncerToggleCommand toggleHandler = new AnnouncerToggleCommand(this);
            toggleCmd.setExecutor(toggleHandler);
            toggleCmd.setTabCompleter(toggleHandler);
        }

        startSeasonSync();
        startTask();
        getLogger().info("Root-Announcer enabled — " + effectiveLines().size() + " message(s), every "
                + announcerConfig.intervalSeconds() + "s.");
    }

    public void disable() {
        stopSeasonSync();
        stopTask();
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        announcerConfig = AnnouncerConfig.from(cfg);
        if (toggles == null) {
            toggles = new AnnouncerToggleStore(host);
        }
        announcer = new AnnouncerService(this);
        restartSeasonSync();
    }

    public List<String> effectiveLines() {
        List<String> base = announcerConfig.lines();
        List<String> merged = new ArrayList<>();
        String hourly = hourlyReachoutLine();
        if (hourly != null && !hourly.isBlank()) {
            merged.add(hourly);
        }
        if (seasonLines.isEmpty()) {
            merged.addAll(base);
            return List.copyOf(merged);
        }
        if (!announcerConfig.seasonLinesFirst()) {
            merged.addAll(base);
            merged.addAll(seasonLines);
            return List.copyOf(merged);
        }
        merged.addAll(seasonLines);
        merged.addAll(base);
        return List.copyOf(merged);
    }

    private String hourlyReachoutLine() {
        RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(host);
        if (reachout == null) {
            return "";
        }
        return reachout.hourlyAnnouncerLine();
    }

    @Override
    public void applySeasonLines(List<String> lines) {
        List<String> next = lines == null ? List.of() : List.copyOf(lines);
        if (next.equals(seasonLines)) {
            return;
        }
        seasonLines = next;
        Bukkit.getScheduler().runTask(host, () -> {
            getLogger().info("Season announcer lines updated (" + seasonLines.size() + ").");
            restartTask();
        });
    }

    private void startSeasonSync() {
        if (!announcerConfig.cloudSyncEnabled()) {
            return;
        }
        seasonSync = new SeasonSyncTask(this, new SeasonCloudClient(cloudSettings()));
        seasonSync.start(announcerConfig.cloudSyncIntervalMinutes());
        seasonSync.runSafe();
    }

    private void restartSeasonSync() {
        stopSeasonSync();
        startSeasonSync();
    }

    private void stopSeasonSync() {
        if (seasonSync != null) {
            seasonSync.stop();
            seasonSync = null;
        }
    }

    private RootRecordCloudConfig.CloudSettings cloudSettings() {
        return RootRecordCloudConfig.resolve(host, yamlConfig != null ? yamlConfig.config() : null);
    }

    @Override
    public org.bukkit.plugin.Plugin plugin() {
        return host;
    }

    @Override
    public org.bukkit.scheduler.BukkitScheduler scheduler() {
        return getServer().getScheduler();
    }

    @Override
    public Logger logger() {
        return getLogger();
    }

    public AnnouncerToggleStore toggles() {
        return toggles;
    }

    public void restartTask() {
        stopTask();
        startTask();
    }

    private void startTask() {
        if (!announcerConfig.enabled() || effectiveLines().isEmpty()) {
            return;
        }
        long ticks = announcerConfig.intervalSeconds() * 20L;
        announceTask = getServer().getScheduler().runTaskTimer(host, () -> announcer.tick(), ticks, ticks);
    }

    private void stopTask() {
        if (announceTask != null) {
            announceTask.cancel();
            announceTask = null;
        }
    }

    public AnnouncerConfig config() {
        return announcerConfig;
    }

    public AnnouncerService announcer() {
        return announcer;
    }

    public String formatLine(String body) {
        String text = body == null ? "" : body;
        text = text.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
        text = text.replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
        text = text.replace("{next_dividend_countdown}", nextDividendCountdown());
        text = text.replace("{next_dividend_hst}", nextDividendHstLabel());
        String suffix = announcerConfig.muteSuffix();
        if (suffix != null && !suffix.isBlank()) {
            text = text + suffix;
        }
        return colorize(text);
    }

    private static String nextDividendCountdown() {
        ZonedDateTime now = ZonedDateTime.now(HST);
        ZonedDateTime next = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusMonths(1);
        }
        Duration d = Duration.between(now, next);
        long mins = Math.max(0, d.toMinutes());
        long days = mins / (60 * 24);
        long hours = (mins % (60 * 24)) / 60;
        long minutes = mins % 60;
        return days + "d " + hours + "h " + minutes + "m";
    }

    private static String nextDividendHstLabel() {
        ZonedDateTime now = ZonedDateTime.now(HST);
        ZonedDateTime next = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusMonths(1);
        }
        return next.getMonth().name().substring(0, 1)
                + next.getMonth().name().substring(1).toLowerCase()
                + " " + next.getDayOfMonth()
                + ", " + next.getYear()
                + " 12:00 AM HST";
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String key) {
        return colorize(rawMsg(key));
    }

    public String rawMsg(String key) {
        return yamlConfig.config().getString("messages." + key, key);
    }
}
