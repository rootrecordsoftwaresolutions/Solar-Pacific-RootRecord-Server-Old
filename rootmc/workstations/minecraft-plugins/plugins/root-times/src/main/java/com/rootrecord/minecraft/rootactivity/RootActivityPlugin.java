package com.rootrecord.minecraft.rootactivity;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootactivity.command.RootActivityAdminCommand;
import com.rootrecord.minecraft.rootactivity.command.TimezoneCommand;
import com.rootrecord.minecraft.rootactivity.config.ActivityConfig;
import com.rootrecord.minecraft.rootactivity.data.ActivityStore;
import com.rootrecord.minecraft.rootactivity.ip.IpTimezoneResolver;
import com.rootrecord.minecraft.rootactivity.listener.ActivityListener;
import com.rootrecord.minecraft.rootactivity.service.ActivityService;
import com.rootrecord.minecraft.rootactivity.tracker.ActivityTracker;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class RootActivityPlugin {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootActivityPlugin(org.bukkit.plugin.java.JavaPlugin host) {
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

    private RootRecordYamlConfig yamlConfig;
    private ActivityConfig config;
    private ActivityStore store;
    private IpTimezoneResolver ipResolver;
    private ActivityTracker tracker;
    private ActivityService service;

    public void enable() {
        RootRecordFolders.ensureDir(host);
        RootRecordCloudConfig.ensureDefaults(host);
        yamlConfig = new RootRecordYamlConfig(host, RootRecordFolders.ROOT_ACTIVITY_CONFIG, "root-activity.yml");
        reloadLocalConfig();

        var timezone = getCommand("timezone");
        if (timezone != null) {
            timezone.setExecutor(new TimezoneCommand(this));
        }
        var admin = getCommand("rootactivity");
        if (admin != null) {
            admin.setExecutor(new RootActivityAdminCommand(this));
        }

        getServer().getPluginManager().registerEvents(new ActivityListener(this), host);
        getLogger().info("Root-Activity enabled — /timezone (/activity) for server peak in your clock.");
    }

    public void disable() {
        if (tracker != null) {
            tracker.stop();
        }
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        config = ActivityConfig.from(host, cfg);
        store = new ActivityStore(config);
        try {
            if (config.mysqlConfigured()) {
                store.initSchema();
            }
        } catch (Exception ex) {
            getLogger().severe("MySQL init failed: " + ex.getMessage());
        }
        ipResolver = new IpTimezoneResolver(config);
        if (tracker == null) {
            tracker = new ActivityTracker(host, store);
        }
        service = new ActivityService(host, config, store, ipResolver, tracker);
        service.reloadTracker();
    }

    public ActivityConfig activityConfig() {
        return config;
    }

    public ActivityService service() {
        return service;
    }

    public String msg(String key) {
        return colorize(rawMsg(key));
    }

    public String msg(String key, Map<String, String> placeholders) {
        String text = rawMsg(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            text = text.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return colorize(text);
    }

    public String rawMsg(String key) {
        String prefix = config.prefix();
        String body = config.messages().getOrDefault(key, "&7[" + key + "]");
        if (prefix == null || prefix.isBlank()) {
            return body;
        }
        return prefix + body;
    }

    public String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
