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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootUpkeepPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private UpkeepConfig config;
    private UpkeepStateStore state;
    private LastLoginStore lastLoginStore;
    private InactivityTaxPendingStore pendingStore;
    private InactivityTaxService taxService;
    private InactivityTaxScheduler scheduler;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_UPKEEP_CONFIG, "root-upkeep.yml");
        state = new UpkeepStateStore(this);
        RootUpkeepCommand command = new RootUpkeepCommand(this);
        var rootCmd = getCommand("rootupkeep");
        if (rootCmd != null) {
            rootCmd.setExecutor(command);
            rootCmd.setTabCompleter(command);
        }
        reloadAll();
        getServer().getPluginManager().registerEvents(new InactivityTaxJoinListener(this), this);
        getLogger().info("Root-Upkeep enabled — inactivity tax for players, towns, and nations.");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    public void reloadAll() {
        yaml.load();
        config = UpkeepConfig.from(this, yaml.config());
        state.load();
        lastLoginStore = new LastLoginStore(config);
        pendingStore = new InactivityTaxPendingStore(config);
        try {
            pendingStore.initSchema();
        } catch (Exception ex) {
            getLogger().warning("Inactivity tax pending table init failed: " + ex.getMessage());
        }
        taxService = buildTaxService();
        if (scheduler == null) {
            scheduler = new InactivityTaxScheduler(this, state);
        }
        scheduler.stop();
        scheduler.start();
    }

    private InactivityTaxService buildTaxService() {
        Economy economy = economy();
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(this);
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
}
