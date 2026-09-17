package com.rootrecord.minecraft.rootterritories;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootterritories.bluemap.BlueMapTerritoryMarkers;
import com.rootrecord.minecraft.rootterritories.command.NationFoundingCommand;
import com.rootrecord.minecraft.rootterritories.command.TerritoriesCommand;
import com.rootrecord.minecraft.rootterritories.config.TerritoriesConfig;
import com.rootrecord.minecraft.rootterritories.listener.NationFoundingListener;
import com.rootrecord.minecraft.rootterritories.listener.TerritoryMoveListener;
import com.rootrecord.minecraft.rootterritories.listener.TerritoryRulesListener;
import com.rootrecord.minecraft.rootterritories.listener.TownyRefreshListener;
import com.rootrecord.minecraft.rootterritories.listener.WildernessAlertListener;
import com.rootrecord.minecraft.rootterritories.nation.NationFoundingConfig;
import com.rootrecord.minecraft.rootterritories.nation.NationFoundingService;
import com.rootrecord.minecraft.rootterritories.nation.NationFoundingStore;
import com.rootrecord.minecraft.rootterritories.particle.TerritoryParticleTask;
import com.rootrecord.minecraft.rootterritories.prefs.TerritoryNotificationStore;
import com.rootrecord.minecraft.rootterritories.service.TerritoryService;
import com.rootrecord.minecraft.rootterritories.service.WildernessAlertService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

public final class RootTerritoriesPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRecordYamlConfig yaml;
    private TerritoriesConfig config;
    private TerritoryService territories;
    private BlueMapTerritoryMarkers bluemap;
    private TerritoryParticleTask particles;
    private WildernessAlertService wildernessAlerts;
    private TerritoryNotificationStore notifications;
    private NationFoundingStore nationFoundingStore;
    private NationFoundingService nationFounding;
    private BukkitTask refreshTask;
    private BukkitTask pendingRebuild;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_TERRITORIES_CONFIG, "root-territories.yml");
        territories = new TerritoryService(this, new TerritoriesConfig(yaml.config()));
        bluemap = new BlueMapTerritoryMarkers(this, territories);
        wildernessAlerts = new WildernessAlertService(territories);
        notifications = new TerritoryNotificationStore(this);
        nationFoundingStore = new NationFoundingStore(this);
        nationFounding = new NationFoundingService(
                this,
                new NationFoundingConfig(yaml.config()),
                nationFoundingStore,
                territories);

        reloadAll();

        TerritoriesCommand handler = new TerritoriesCommand(this, territories, notifications);
        NationFoundingCommand foundingCmd = new NationFoundingCommand(this);
        var rootCmd = getCommand("rootterritories");
        if (rootCmd != null) {
            rootCmd.setExecutor(handler);
            rootCmd.setTabCompleter(handler);
        }
        var territoriesCmd = getCommand("territories");
        if (territoriesCmd != null) {
            territoriesCmd.setExecutor(handler);
            territoriesCmd.setTabCompleter(handler);
        }
        var foundingCommand = getCommand("nationfounding");
        if (foundingCommand != null) {
            foundingCommand.setExecutor(foundingCmd);
            foundingCommand.setTabCompleter(foundingCmd);
        }

        getServer().getPluginManager().registerEvents(new TerritoryMoveListener(territories, notifications), this);
        getServer().getPluginManager().registerEvents(new WildernessAlertListener(wildernessAlerts), this);
        TerritoryRulesListener.register(this);
        NationFoundingListener.register(this);
        TownyRefreshListener.register(this);
        bluemap.register();
        startRefreshTask();
        getServer().getScheduler().runTaskLater(this, () -> rebuildNow(true), 100L);

        getLogger().info("Root-Territories enabled — nation/town rings and border messages.");
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        stopRefreshTask();
        if (particles != null) {
            particles.stop();
        }
        if (pendingRebuild != null) {
            pendingRebuild.cancel();
            pendingRebuild = null;
        }
        if (bluemap != null) {
            bluemap.unregister();
        }
    }

    public void reloadAll() {
        yaml.load();
        config = new TerritoriesConfig(yaml.config());
        territories.setConfig(config);
        if (nationFoundingStore != null) {
            nationFoundingStore.reload();
        }
        if (nationFounding != null) {
            nationFounding = new NationFoundingService(
                    this,
                    new NationFoundingConfig(yaml.config()),
                    nationFoundingStore,
                    territories);
            nationFounding.bootstrapGrandfather();
        }
        stopRefreshTask();
        if (particles != null) {
            particles.stop();
            particles.start();
        }
        startRefreshTask();
        rebuildNow(true);
        bluemap.syncIfPresent();
    }

    public void scheduleRebuild(boolean logSummary) {
        if (!territories.usesTownyZones()) {
            return;
        }
        if (pendingRebuild != null) {
            pendingRebuild.cancel();
        }
        pendingRebuild = getServer().getScheduler().runTaskLater(this, () -> rebuildNow(logSummary), 40L);
    }

    private void rebuildNow(boolean logSummary) {
        territories.rebuild(logSummary);
        territories.syncAllOnlinePlayersSilently();
        if (particles != null) {
            particles.rebuildRings();
        }
        bluemap.syncIfPresent();
    }

    private void startRefreshTask() {
        stopRefreshTask();
        if (!territories.usesTownyZones()) {
            return;
        }
        long ticks = Math.max(1L, config.refreshMinutes()) * 60L * 20L;
        refreshTask = getServer().getScheduler().runTaskTimer(this, () -> rebuildNow(true), ticks, ticks);
    }

    private void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public TerritoryService territories() {
        return territories;
    }

    public TerritoryNotificationStore notifications() {
        return notifications;
    }

    public NationFoundingService nationFounding() {
        return nationFounding;
    }

    /** Refresh spawn wall markers on BlueMap (after /rootspawn reload). */
    public void syncMapMarkers() {
        territories.reloadSpawnZone();
        bluemap.syncIfPresent();
    }
}
