package com.rootrecord.minecraft.rootroad;

import com.rootrecord.minecraft.common.RootMcServerDisplay;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootroad.command.RoadCommand;
import com.rootrecord.minecraft.rootroad.listener.RoadJoinListener;
import com.rootrecord.minecraft.rootroad.listener.RoadProgressListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public final class RootRoadPlugin {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootRoadPlugin(org.bukkit.plugin.java.JavaPlugin host) {
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

    public static final String CONFIG_FILE = "root-road.yml";

    private RootRecordYamlConfig yaml;
    private RoadProgressStore progress;
    private RoadGuideService guide;
    private BukkitTask unsafeWarnTask;

    private boolean enabledFlag = true;
    private boolean restartIncompleteOnQuit = true;
    private double loanAmountGold = 100;
    private boolean unsafeWarnEnabled = true;
    private long unsafeWarnIntervalTicks = 12L * 20L;
    private String prefix = "&6[Road] &r";

    public void enable() {
        RootRecordFolders.ensureDir(host);
        yaml = new RootRecordYamlConfig(host, CONFIG_FILE, CONFIG_FILE);
        yaml.load();
        progress = new RoadProgressStore(this);
        progress.load();
        guide = new RoadGuideService(this, progress);
        reloadLocalConfig();

        RoadCommand command = new RoadCommand(this, guide);
        var road = getCommand("road");
        if (road != null) {
            road.setExecutor(command);
            road.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new RoadJoinListener(this, guide), host);
        getServer().getPluginManager().registerEvents(new RoadProgressListener(this, guide), host);
        startUnsafeWarnTask();
        getLogger().info("Root-Road enabled - guided first join (/rtp -> /loan take "
                + loanAmountGold + " -> /c).");
    }

    public void disable() {
        if (unsafeWarnTask != null) {
            unsafeWarnTask.cancel();
            unsafeWarnTask = null;
        }
        if (progress != null) {
            progress.save();
        }
    }

    public void reloadAll() {
        if (yaml != null) {
            yaml.reload();
        }
        reloadLocalConfig();
        startUnsafeWarnTask();
    }

    private void reloadLocalConfig() {
        FileConfiguration cfg = yaml.config();
        enabledFlag = cfg.getBoolean("enabled", true);
        restartIncompleteOnQuit = cfg.getBoolean("restart-incomplete-on-quit", true);
        loanAmountGold = Math.max(1, cfg.getDouble("loan.amount-g", 100));
        unsafeWarnEnabled = cfg.getBoolean("unsafe-warn.enabled", true);
        long seconds = Math.max(5L, cfg.getLong("unsafe-warn.interval-seconds", 12));
        unsafeWarnIntervalTicks = seconds * 20L;
        prefix = cfg.getString("messages.prefix", "");
    }

    private void startUnsafeWarnTask() {
        if (unsafeWarnTask != null) {
            unsafeWarnTask.cancel();
            unsafeWarnTask = null;
        }
        if (!enabledFlag || !unsafeWarnEnabled) {
            return;
        }
        unsafeWarnTask = Bukkit.getScheduler().runTaskTimer(host, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                guide.warnIfUnsafe(player);
            }
        }, unsafeWarnIntervalTicks, unsafeWarnIntervalTicks);
    }

    public boolean enabledFlag() {
        return enabledFlag;
    }

    public boolean restartIncompleteOnQuit() {
        return restartIncompleteOnQuit;
    }

    public double loanAmountGold() {
        return loanAmountGold;
    }

    public boolean unsafeWarnEnabled() {
        return unsafeWarnEnabled;
    }

    public RoadGuideService guide() {
        return guide;
    }

    public String msg(String key) {
        return colorize(RootMcServerDisplay.apply(host, prefix + rawMsg(key)));
    }

    public String rawMsg(String key) {
        return yaml.config().getString("messages." + key, key);
    }

    public List<String> rawList(String key) {
        return yaml.config().getStringList("messages." + key);
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', RootMcServerDisplay.apply(host, raw));
    }
}
