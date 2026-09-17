package com.rootrecord.minecraft.rootbonds;

import com.rootrecord.minecraft.common.RootMcBondTransferService;
import com.rootrecord.minecraft.common.RootMcBondIncomeService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.TreasuryIncomeHub;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootbonds.command.BondsCommand;
import com.rootrecord.minecraft.rootbonds.command.RootBondsAdminCommand;
import com.rootrecord.minecraft.rootbonds.config.BondsConfig;
import com.rootrecord.minecraft.rootbonds.config.BondsMessages;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;
import com.rootrecord.minecraft.rootbonds.service.BondTransferService;
import com.rootrecord.minecraft.rootbonds.gui.BondCreateListener;
import com.rootrecord.minecraft.rootbonds.gui.BondOwnershipListener;
import com.rootrecord.minecraft.rootbonds.gui.BondTransferListener;
import com.rootrecord.minecraft.rootbonds.gui.GovBondNotifyListener;
import com.rootrecord.minecraft.rootbonds.gui.BondNoteInteractListener;
import com.rootrecord.minecraft.rootbonds.gui.BondsMenuListener;
import com.rootrecord.minecraft.rootbonds.gui.BondsMenuRegistry;
import com.rootrecord.minecraft.rootbonds.service.BondCloudSync;
import com.rootrecord.minecraft.rootbonds.service.BondDayScheduler;
import com.rootrecord.minecraft.rootbonds.service.BondExpiryService;
import com.rootrecord.minecraft.rootbonds.service.BondIncomeService;
import com.rootrecord.minecraft.rootbonds.service.BondPrincipalResolver;
import com.rootrecord.minecraft.rootbonds.service.BondService;
import com.rootrecord.minecraft.rootbonds.service.GovernmentBondSettingsService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class RootBondsPlugin extends JavaPlugin {

    private RootRecordYamlConfig yamlConfig;
    private BondsConfig bondsConfig;
    private BondsMessages messages;
    private BondsStore store;
    private BondService bonds;
    private BondTransferService bondTransfer;
    private BondIncomeService income;
    private BondCloudSync cloudSync;
    private BondDayScheduler dayScheduler;
    private BondExpiryService expiryService;
    private BondPrincipalResolver principalResolver;
    private GovernmentBondSettingsService govSettings;
    private BondCreateListener createListener;
    private BondsMenuRegistry menuRegistry;
    private boolean mysqlReady;

    @Override
    public void onEnable() {
        TreasuryIncomeHub.setLogger(getLogger());
        RootRecordFolders.ensureDir(this);
        yamlConfig = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_BONDS_CONFIG, "root-bonds.yml");
        yamlConfig.load();
        bonds = new BondService(this);
        bondTransfer = new BondTransferService(this, bonds);
        income = new BondIncomeService(this);
        cloudSync = new BondCloudSync(this);
        expiryService = new BondExpiryService(this);
        principalResolver = new BondPrincipalResolver(this);
        govSettings = new GovernmentBondSettingsService(this);
        dayScheduler = new BondDayScheduler(this, income, expiryService);
        menuRegistry = new BondsMenuRegistry(this);
        createListener = new BondCreateListener(this);
        registerCommands();
        getServer().getPluginManager().registerEvents(new BondsMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new BondNoteInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new BondTransferListener(this), this);
        getServer().getPluginManager().registerEvents(new BondOwnershipListener(this), this);
        getServer().getPluginManager().registerEvents(new GovBondNotifyListener(this), this);
        getServer().getPluginManager().registerEvents(createListener, this);
        getServer().getServicesManager().register(
                RootMcBondTransferService.class, bondTransfer, this, ServicePriority.Normal);
        getServer().getServicesManager().register(
                RootMcBondIncomeService.class, income, this, ServicePriority.Normal);
        reloadLocalConfig();
    }

    @Override
    public void onDisable() {
        if (bondTransfer != null) {
            getServer().getServicesManager().unregister(RootMcBondTransferService.class, bondTransfer);
        }
        if (income != null) {
            getServer().getServicesManager().unregister(RootMcBondIncomeService.class, income);
        }
        if (dayScheduler != null) {
            dayScheduler.stop();
        }
        if (income != null) {
            income.close();
        }
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        bondsConfig = BondsConfig.from(this, cfg);
        bondsConfig = withUpkeepGrace(bondsConfig);
        messages = new BondsMessages(cfg);
        store = new BondsStore(bondsConfig);
        mysqlReady = true;
        try {
            store.initSchema();
        } catch (Exception ex) {
            mysqlReady = false;
            getLogger().severe("Bonds MySQL init failed: " + ex.getMessage());
        }
        bonds.reload(bondsConfig, store);
        principalResolver.reload(bondsConfig, store);
        if (govSettings != null) {
            govSettings.reload(store);
        }
        cloudSync.reload(bondsConfig, store, cfg, principalResolver);
        income.reload(bondsConfig, store, cloudSync, principalResolver, bonds);
        if (mysqlReady && bondsConfig.enabled()) {
            dayScheduler.start(bondsConfig);
        } else if (dayScheduler != null) {
            dayScheduler.stop();
        }
    }

    /** Called by RootMC's single ordered Minecraft-day coordinator. */
    public void processMcDayRollover(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        if (dayScheduler == null || !mysqlReady || !bondsConfig.enabled()) {
            onComplete.run();
            return;
        }
        dayScheduler.processMcDayRollover(firstCompletedDay, currentMcDayId, onComplete);
    }

    private BondsConfig withUpkeepGrace(BondsConfig base) {
        File upkeep = RootRecordFolders.configFile(this, RootRecordFolders.ROOT_UPKEEP_CONFIG);
        if (!upkeep.isFile()) {
            return base;
        }
        int grace = YamlConfiguration.loadConfiguration(upkeep).getInt("inactivity.grace-days", base.graceDays());
        if (grace == base.graceDays()) {
            return base;
        }
        getLogger().info("Root-Bonds using inactivity grace-days from root-upkeep.yml (" + grace + ").");
        return base.withGraceDays(grace);
    }


    private void registerCommands() {
        BondsCommand bondsCommand = new BondsCommand(this);
        var bondsCmd = getCommand("bonds");
        if (bondsCmd != null) {
            bondsCmd.setExecutor(bondsCommand);
            bondsCmd.setTabCompleter(bondsCommand);
        }
        RootBondsAdminCommand adminCommand = new RootBondsAdminCommand(this);
        var rootBondsCmd = getCommand("rootbonds");
        if (rootBondsCmd != null) {
            rootBondsCmd.setExecutor(adminCommand);
            rootBondsCmd.setTabCompleter(adminCommand);
        }
    }

    public BondPrincipalResolver principalResolver() {
        return principalResolver;
    }

    public BondService bonds() {
        return bonds;
    }

    public BondTransferService bondTransfer() {
        return bondTransfer;
    }

    public BondIncomeService bondIncome() {
        return income;
    }

    public GovernmentBondSettingsService govSettings() {
        return govSettings;
    }

    public BondsStore store() {
        return store;
    }

    public BondService.BondHeartbeatSummary bondHeartbeatSummary(java.util.UUID ownerUuid) {
        return bonds == null ? null : bonds.heartbeatSummary(ownerUuid).orElse(null);
    }

    public BondsMenuRegistry menuRegistry() {
        return menuRegistry;
    }

    public BondCloudSync cloudSync() {
        return cloudSync;
    }

    public BondCreateListener createListener() {
        return createListener;
    }

    public String msg(String key) {
        return messages == null ? key : messages.msg(key);
    }

    public boolean mysqlReady() {
        return mysqlReady;
    }
}
