package com.rootrecord.minecraft.rootbanner;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootbanner.command.BannerCommand;
import com.rootrecord.minecraft.rootbanner.order.BannerShopService;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class RootBannerPlugin {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootBannerPlugin(org.bukkit.plugin.java.JavaPlugin host) {
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
    private BannerShopService shop;
    private boolean enabledFlag = true;
    private DyeColor defaultBase = DyeColor.BLACK;
    private DyeColor defaultInk = DyeColor.WHITE;
    private double blankPrice = 5;
    private double letterPrice = 10;
    private double symbolPrice = 25;
    private int maxBanners = 36;
    private int confirmSeconds = 60;
    private String prefix = "&6[Banner] &r";
    private List<String> helpLines = List.of();

    public void enable() {
        RootRecordFolders.ensureDir(host);
        yamlConfig = new RootRecordYamlConfig(host, RootRecordFolders.ROOT_BANNER_CONFIG, "root-banner.yml");
        yamlConfig.load();
        shop = new BannerShopService(this);
        reloadLocalConfig();

        var banner = getCommand("banner");
        if (banner != null) {
            BannerCommand handler = new BannerCommand(this, shop);
            banner.setExecutor(handler);
            banner.setTabCompleter(handler);
        }

        getLogger().info("Root-Banner enabled — /banner ("
                + "blank " + blankPrice + " G, letter " + letterPrice + " G, symbol "
                + symbolPrice + " G).");
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        enabledFlag = cfg == null || cfg.getBoolean("enabled", true);
        defaultBase = parseColor(cfg != null ? cfg.getString("defaults.base", "black") : "black", DyeColor.BLACK);
        defaultInk = parseColor(cfg != null ? cfg.getString("defaults.ink", "white") : "white", DyeColor.WHITE);
        blankPrice = cfg != null ? cfg.getDouble("prices.blank", 5) : 5;
        letterPrice = cfg != null ? cfg.getDouble("prices.letter", 10) : 10;
        symbolPrice = cfg != null ? cfg.getDouble("prices.number-or-symbol", 25) : 25;
        maxBanners = Math.max(1, cfg != null ? cfg.getInt("limits.max-banners", 36) : 36);
        confirmSeconds = Math.max(10, cfg != null ? cfg.getInt("limits.confirm-seconds", 60) : 60);
        prefix = cfg != null ? cfg.getString("messages.prefix", "") : "";
        helpLines = cfg != null ? cfg.getStringList("messages.help") : List.of();
    }

    private static DyeColor parseColor(String raw, DyeColor fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return DyeColor.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public boolean enabled() {
        return enabledFlag;
    }

    public DyeColor defaultBase() {
        return defaultBase;
    }

    public DyeColor defaultInk() {
        return defaultInk;
    }

    public double blankPrice() {
        return blankPrice;
    }

    public double letterPrice() {
        return letterPrice;
    }

    public double symbolPrice() {
        return symbolPrice;
    }

    public int maxBanners() {
        return maxBanners;
    }

    public int confirmSeconds() {
        return confirmSeconds;
    }

    public List<String> helpLines() {
        return helpLines;
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String key) {
        return colorize(prefix + rawMsg(key));
    }

    public String rawMsg(String key) {
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        if (cfg == null) {
            return key;
        }
        return cfg.getString("messages." + key, key);
    }
    public void disable() {
    }
}
