package com.rootrecord.minecraft.roottimes.uptime;

import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.management.ManagementFactory;

/** Current uptime + longest recorded continuous run (persisted across restarts). */
public final class UptimeTracker {

    private static final String FILE = "times-uptime.yml";

    private final RootTimesPlugin plugin;
    private final long bootAtMs;
    private long longestMs;

    public UptimeTracker(RootTimesPlugin plugin) {
        this.plugin = plugin;
        this.bootAtMs = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getUptime();
        this.longestMs = loadLongest();
    }

    public long currentUptimeMs() {
        return Math.max(0L, System.currentTimeMillis() - bootAtMs);
    }

    public long longestUptimeMs() {
        return Math.max(longestMs, currentUptimeMs());
    }

    public void onDisable() {
        long current = currentUptimeMs();
        if (current > longestMs) {
            longestMs = current;
            saveLongest(longestMs);
        }
    }

    private long loadLongest() {
        File file = new File(plugin.getDataFolder().getParentFile(), "RootMC/" + FILE);
        if (!file.isFile()) {
            file = new File(plugin.getDataFolder(), FILE);
        }
        if (!file.isFile()) {
            return 0L;
        }
        try {
            return Math.max(0L, YamlConfiguration.loadConfiguration(file).getLong("longest-uptime-ms", 0L));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private void saveLongest(long ms) {
        try {
            File dir = new File(plugin.getDataFolder().getParentFile(), "RootMC");
            if (!dir.isDirectory()) {
                dir = plugin.getDataFolder();
            }
            dir.mkdirs();
            File file = new File(dir, FILE);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("longest-uptime-ms", Math.max(0L, ms));
            yaml.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save longest uptime: " + ex.getMessage());
        }
    }
}
