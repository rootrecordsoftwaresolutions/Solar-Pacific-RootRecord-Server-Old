package com.rootrecord.minecraft.rootannouncer.cloud;

import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Level;

/** Periodically pulls season announcer lines from RootMC API. */
public final class SeasonSyncTask {

    private final RootAnnouncerPluginRef plugin;
    private final SeasonCloudClient client;
    private BukkitTask repeatingTask;

    public SeasonSyncTask(RootAnnouncerPluginRef plugin, SeasonCloudClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void start(long intervalMinutes) {
        stop();
        long ticks = Math.max(1L, intervalMinutes) * 60L * 20L;
        repeatingTask = plugin.scheduler().runTaskTimerAsynchronously(
                plugin.plugin(),
                this::runSafe,
                40L,
                ticks);
    }

    public void stop() {
        if (repeatingTask != null) {
            repeatingTask.cancel();
            repeatingTask = null;
        }
    }

    public void runSafe() {
        try {
            List<String> lines = client.fetchSeasonLines();
            plugin.applySeasonLines(lines);
        } catch (Exception ex) {
            plugin.logger().log(Level.FINE, "Season announcer sync skipped: " + ex.getMessage());
        }
    }

    /** Minimal surface so cloud package does not depend on the plugin class directly. */
    public interface RootAnnouncerPluginRef {
        org.bukkit.plugin.Plugin plugin();

        org.bukkit.scheduler.BukkitScheduler scheduler();

        java.util.logging.Logger logger();

        void applySeasonLines(List<String> lines);
    }
}
