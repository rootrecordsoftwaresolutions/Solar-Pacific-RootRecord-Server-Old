package com.rootrecord.minecraft.rootwebstat;

import com.rootrecord.minecraft.common.RootMcServerDisplay;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

public final class RootWebstatPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRecordYamlConfig yaml;
    private WebstatConfig config;
    private StatsEngine stats;
    private WebstatDataCatalog dataCatalog;
    private WebstatHttpServer http;
    private CloudPushService cloud;
    private VanillaDayWatcher dayWatcher;
    private BukkitTask refreshTask;
    private BukkitTask pushTask;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_WEBSTAT_CONFIG, "root-webstat.yml");
        yaml.load();
        config = WebstatConfig.from(this, yaml.config());
        stats = new StatsEngine(this);
        dataCatalog = new WebstatDataCatalog(this);
        http = new WebstatHttpServer(this);
        cloud = new CloudPushService(this);
        dayWatcher = new VanillaDayWatcher(this);

        PluginCommand cmd = getCommand("webstat");
        if (cmd != null) {
            WebstatCommand handler = new WebstatCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        if (!config.enabled()) {
            getLogger().info("Root-Webstat disabled in config.");
            return;
        }

        refreshStats(false);
        http.start();
        scheduleTasks();
        getLogger().info("Root-Webstat enabled for \"" + displayServerName() + "\" on port " + config.port());
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        cancelTasks();
        if (http != null) {
            http.stop();
        }
    }

    public void reloadAll() {
        cancelTasks();
        if (http != null) {
            http.stop();
        }
        yaml.load();
        config = WebstatConfig.from(this, yaml.config());
        if (!config.enabled()) {
            getLogger().info("Root-Webstat disabled after reload.");
            return;
        }
        refreshStats(false);
        http.start();
        scheduleTasks();
    }

    /** Vanilla day crossed — recompute snapshot for the completed day and publish. */
    public void onVanillaDayRollover(long completedDayId, long currentDayId, long fullTime) {
        getLogger().info("Vanilla day " + completedDayId + " → " + currentDayId + " — recomputing webstat.");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            String json = stats.recompute(completedDayId, currentDayId, fullTime);
            dataCatalog.recomputeAll();
            cloud.writeLocalSnapshot(json);
            if (config.pushEnabled()) {
                cloud.pushNow();
            }
        });
    }

    public void refreshStats(boolean push) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            String json = stats.recompute();
            dataCatalog.recomputeAll();
            cloud.writeLocalSnapshot(json);
            if (push && config.pushEnabled()) {
                cloud.pushNow();
            }
        });
    }

    private void scheduleTasks() {
        cancelTasks();
        if (config.onVanillaDay()) {
            dayWatcher.start();
        }
        if (config.refreshSeconds() > 0) {
            long refreshTicks = Math.max(20L, config.refreshSeconds() * 20L);
            refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    this, () -> refreshStats(false), refreshTicks, refreshTicks);
        }
        if (config.pushEnabled() && config.pushIntervalSeconds() > 0) {
            long pushTicks = Math.max(20L * 60L, config.pushIntervalSeconds() * 20L);
            pushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    this, cloud::pushNow, pushTicks, pushTicks);
        }
    }

    private void cancelTasks() {
        if (dayWatcher != null) {
            dayWatcher.stop();
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (pushTask != null) {
            pushTask.cancel();
            pushTask = null;
        }
    }

    public String displayServerName() {
        if (config != null && config.serverNameOverride() != null && !config.serverNameOverride().isBlank()) {
            return config.serverNameOverride();
        }
        RegisteredServiceProvider<RootCoreApi> rsp =
                Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
        if (rsp != null && rsp.getProvider() != null) {
            try {
                String n = rsp.getProvider().serverName();
                if (n != null && !n.isBlank()) {
                    return n;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return RootMcServerDisplay.serverName(this);
    }

    public WebstatConfig config() {
        return config;
    }

    public StatsEngine stats() {
        return stats;
    }

    public WebstatDataCatalog dataCatalog() {
        return dataCatalog;
    }

    public WebstatHttpServer http() {
        return http;
    }

    public CloudPushService cloud() {
        return cloud;
    }

    public VanillaDayWatcher dayWatcher() {
        return dayWatcher;
    }
}
