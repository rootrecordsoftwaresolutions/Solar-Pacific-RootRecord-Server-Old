package com.rootrecord.minecraft.rootupkeep;

import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootupkeep.command.RootUpkeepCommand;
import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;
import com.rootrecord.minecraft.rootupkeep.data.InactivityTaxPendingStore;
import com.rootrecord.minecraft.rootupkeep.data.LastLoginStore;
import com.rootrecord.minecraft.rootupkeep.data.UpkeepStateStore;
import com.rootrecord.minecraft.rootupkeep.listener.InactivityTaxJoinListener;
import com.rootrecord.minecraft.rootupkeep.schedule.InactivityTaxScheduler;
import com.rootrecord.minecraft.rootupkeep.service.InactivityTaxService;
import com.rootrecord.minecraft.rootupkeep.service.TownDailyTaxService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootUpkeepPlugin {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootUpkeepPlugin(org.bukkit.plugin.java.JavaPlugin host) {
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

    private RootRecordYamlConfig yaml;
    private UpkeepConfig config;
    private UpkeepStateStore state;
    private LastLoginStore lastLoginStore;
    private InactivityTaxPendingStore pendingStore;
    private InactivityTaxService taxService;
    private InactivityTaxScheduler scheduler;
    private TownDailyTaxService townDailyTaxService;

    public void enable() {
        RootRecordFolders.ensureDir(host);
        yaml = new RootRecordYamlConfig(host, RootRecordFolders.ROOT_UPKEEP_CONFIG, "root-upkeep.yml");
        state = new UpkeepStateStore(host);
        RootUpkeepCommand command = new RootUpkeepCommand(this);
        var rootCmd = getCommand("rootupkeep");
        if (rootCmd != null) {
            rootCmd.setExecutor(command);
            rootCmd.setTabCompleter(command);
        }
        reloadAll();
        getServer().getPluginManager().registerEvents(new InactivityTaxJoinListener(this), host);
        getLogger().info("Root-Upkeep enabled — inactivity tax + town daily tax (MC day).");
    }

    public void disable() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    public void reloadAll() {
        yaml.load();
        config = UpkeepConfig.from(host, yaml.config());
        state.load();
        lastLoginStore = new LastLoginStore(config);
        pendingStore = new InactivityTaxPendingStore(config);
        try {
            pendingStore.initSchema();
        } catch (Exception ex) {
            getLogger().warning("Inactivity tax pending table init failed: " + ex.getMessage());
        }
        taxService = buildTaxService();
        townDailyTaxService = new TownDailyTaxService(this, state);
        if (scheduler == null) {
            scheduler = new InactivityTaxScheduler(this, state);
        }
        scheduler.stop();
        scheduler.start();
    }

    private InactivityTaxService buildTaxService() {
        Economy economy = economy();
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(host);
        if (economy == null) {
            getLogger().warning("Vault economy not found — inactivity tax disabled.");
            return null;
        }
        if (treasury == null) {
            getLogger().warning("Root treasury not found — inactivity tax disabled.");
            return null;
        }
        if (!config.mysqlConfigured()) {
            getLogger().warning("MySQL not available — inactivity tax cannot read last-login times.");
        }
        return new InactivityTaxService(this, config, lastLoginStore, economy, treasury);
    }

    private static Economy economy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    public UpkeepConfig config() {
        return config;
    }

    public InactivityTaxService taxService() {
        return taxService;
    }

    public InactivityTaxScheduler scheduler() {
        return scheduler;
    }

    public InactivityTaxPendingStore pendingStore() {
        return pendingStore;
    }

    /**
     * Called by RootMC on Minecraft day rollover (wakeup only).
     * Tax itself is once per real HST calendar day — not per MC day.
     */
    public void processMcDayInactivityTax(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        if (!config.enabled() || scheduler == null) {
            onComplete.run();
            return;
        }
        scheduler.processMcDayRollover(firstCompletedDay, currentMcDayId, onComplete);
    }

    /** Town bank tax: rate-percent of balance once per completed Minecraft day → Server Reserve. */
    public void processMcDayTownTax(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        if (townDailyTaxService == null) {
            onComplete.run();
            return;
        }
        townDailyTaxService.processMcDayRollover(firstCompletedDay, currentMcDayId, onComplete);
    }
}
