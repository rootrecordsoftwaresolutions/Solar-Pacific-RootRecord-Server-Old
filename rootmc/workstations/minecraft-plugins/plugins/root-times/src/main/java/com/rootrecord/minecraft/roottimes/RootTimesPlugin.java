package com.rootrecord.minecraft.roottimes;

import com.rootrecord.minecraft.common.FancyUiConfig;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import com.rootrecord.minecraft.roottimes.afk.AfkService;
import com.rootrecord.minecraft.roottimes.api.RootTimesApi;
import com.rootrecord.minecraft.roottimes.api.RootTimesApiImpl;
import com.rootrecord.minecraft.roottimes.clock.ClockService;
import com.rootrecord.minecraft.roottimes.cloud.TimesCloudReporter;
import com.rootrecord.minecraft.roottimes.command.AfkCommand;
import com.rootrecord.minecraft.roottimes.command.TimeCommand;
import com.rootrecord.minecraft.roottimes.command.TimesCommand;
import com.rootrecord.minecraft.roottimes.config.TimesConfig;
import com.rootrecord.minecraft.roottimes.mysql.PlaytimeStore;
import com.rootrecord.minecraft.roottimes.mysql.TimesMysql;
import com.rootrecord.minecraft.roottimes.track.SessionTracker;
import com.rootrecord.minecraft.roottimes.web.TimesHttpServer;
import com.rootrecord.minecraft.roottimes.welcome.JoinWelcomeListener;
import com.rootrecord.minecraft.roottimes.world.WorldTimeSync;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

import java.util.UUID;

public final class RootTimesPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRecordYamlConfig yamlConfig;
    private TimesConfig timesConfig;
    private TimesMysql mysql;
    private WorldTimeSync worldTimeSync;
    private SessionTracker sessionTracker;
    private AfkService afkService;
    private JoinWelcomeListener welcomeListener;
    private TimesHttpServer httpServer;
    private TimesCloudReporter cloudReporter;
    private RootTimesApiImpl api;
    private com.rootrecord.minecraft.rootactivity.RootActivityPlugin activityFeature;
    private com.rootrecord.minecraft.roottimes.uptime.UptimeTracker uptimeTracker;
    private boolean corePresent;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        FancyUiConfig.load(this);
        corePresent = wireCore();
        if (!corePresent) {
            var repair = RootMcCoreConnection.ensureAndRepair(this);
            getLogger().warning(
                    "Root-Core not present — used RootMcCoreConnection fallback (databaseOk="
                            + repair.databaseOk()
                            + ", cloudOk="
                            + repair.cloudOk()
                            + "). Install Root-Core for licensing and suite updates.");
        }

        yamlConfig = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_TIMES_CONFIG, "root-times.yml");
        yamlConfig.load();
        timesConfig = TimesConfig.from(this, yamlConfig.config());

        checkLicense();
        ClockService.apply(timesConfig);

        mysql = new TimesMysql(timesConfig);
        if (mysql.ready()) {
            try {
                mysql.initSchema();
                getLogger().info(
                        "Playtime schema ready — "
                                + timesConfig.playtimeTable()
                                + " (scope *=global, else server-id="
                                + timesConfig.serverId()
                                + ")");
            } catch (Exception ex) {
                getLogger().severe("MySQL schema init failed: " + ex.getMessage());
            }
        } else {
            getLogger().warning("MySQL not configured — harvest/welcome playtime limited until database.yml is set.");
        }

        worldTimeSync = new WorldTimeSync(this);
        worldTimeSync.start();

        afkService = new AfkService(this);
        afkService.start();

        sessionTracker = new SessionTracker(this);
        sessionTracker.start();

        uptimeTracker = new com.rootrecord.minecraft.roottimes.uptime.UptimeTracker(this);

        welcomeListener = new JoinWelcomeListener(this);
        welcomeListener.start();

        httpServer = new TimesHttpServer(this);
        httpServer.start();

        cloudReporter = new TimesCloudReporter(this);
        cloudReporter.start();

        api = new RootTimesApiImpl(this);
        Bukkit.getServicesManager().register(RootTimesApi.class, api, this, ServicePriority.Normal);

        TimesCommand timesCmd = new TimesCommand(this);
        var times = getCommand("times");
        if (times != null) {
            times.setExecutor(timesCmd);
            times.setTabCompleter(timesCmd);
        }
        var time = getCommand("time");
        if (time != null) {
            time.setExecutor(new TimeCommand(this));
        }
        // Times owns /afk; Essentials delegates when Times is present.
        var afk = getCommand("afk");
        if (afk != null) {
            afk.setExecutor(new AfkCommand(this));
        }

        activityFeature = new com.rootrecord.minecraft.rootactivity.RootActivityPlugin(this);
        activityFeature.enable();

        getLogger().info("Root-Times enabled — day clock + harvest + AFK + activity + welcome + web.");
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (activityFeature != null) {
            activityFeature.disable();
            activityFeature = null;
        }
        if (uptimeTracker != null) {
            uptimeTracker.onDisable();
        }
        ServicesManager sm = Bukkit.getServicesManager();
        if (api != null) {
            sm.unregister(RootTimesApi.class, api);
            api = null;
        }
        if (httpServer != null) {
            httpServer.stop();
        }
        if (cloudReporter != null) {
            cloudReporter.stop();
        }
        if (welcomeListener != null) {
            welcomeListener.stop();
        }
        if (sessionTracker != null) {
            sessionTracker.stop();
        }
        if (afkService != null) {
            afkService.stop();
        }
        if (worldTimeSync != null) {
            worldTimeSync.stop();
        }
    }

    public void reloadAll() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        timesConfig = TimesConfig.from(this, yamlConfig.config());
        ClockService.apply(timesConfig);
        mysql = new TimesMysql(timesConfig);
        if (mysql.ready()) {
            try {
                mysql.initSchema();
            } catch (Exception ex) {
                getLogger().warning("MySQL schema reload failed: " + ex.getMessage());
            }
        }
        if (worldTimeSync != null) {
            worldTimeSync.stop();
            worldTimeSync.start();
        }
        if (afkService != null) {
            afkService.stop();
            afkService.start();
        }
        if (sessionTracker != null) {
            sessionTracker.stop();
            sessionTracker.start();
        }
        if (welcomeListener != null) {
            welcomeListener.stop();
            welcomeListener.start();
        }
        if (httpServer != null) {
            httpServer.stop();
            httpServer.start();
        }
        if (cloudReporter != null) {
            cloudReporter.stop();
            cloudReporter.start();
        }
    }

    private boolean wireCore() {
        try {
            Class<?> apiClass = Class.forName("com.rootrecord.minecraft.rootcore.api.RootCoreApi");
            var reg = Bukkit.getServicesManager().getRegistration(apiClass);
            if (reg == null) {
                return false;
            }
            Object core = reg.getProvider();
            core.getClass().getMethod("ensureCoreFiles").invoke(core);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private void checkLicense() {
        try {
            Class<?> apiClass = Class.forName("com.rootrecord.minecraft.rootcore.api.RootCoreApi");
            var reg = Bukkit.getServicesManager().getRegistration(apiClass);
            if (reg == null) {
                return;
            }
            Object core = reg.getProvider();
            Object licensed = core.getClass().getMethod("isLicensed", String.class).invoke(core, "Root-Times");
            if (licensed instanceof Boolean b && !b) {
                getLogger().warning(
                        "RootCoreApi.isLicensed(Root-Times) returned false — continuing (operator/grace).");
            }
        } catch (ReflectiveOperationException ignored) {
            // Core absent or API mismatch
        }
    }

    public TimesConfig timesConfig() {
        return timesConfig;
    }

    public WorldTimeSync worldTimeSync() {
        return worldTimeSync;
    }

    public TimesMysql mysql() {
        return mysql;
    }

    /**
     * Credit playtime for a player who may be offline (e.g. Ava working via Mannequin autonomy).
     * Same Root-Times tables as a normal online session flush.
     */
    public void creditPlaytime(UUID uuid, String username, long seconds) {
        if (uuid == null || seconds <= 0L || mysql == null || timesConfig == null) {
            return;
        }
        final String name = username == null || username.isBlank() ? "Ava_Ivy" : username;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (var c = mysql.open()) {
                PlaytimeStore.addSession(c, timesConfig, uuid, seconds);
                PlaytimeStore.recordLogin(c, timesConfig, uuid, name);
            } catch (Exception ex) {
                getLogger().fine("creditPlaytime: " + ex.getMessage());
            }
        });
    }

    public AfkService afkService() {
        return afkService;
    }

    public SessionTracker sessionTracker() {
        return sessionTracker;
    }

    public com.rootrecord.minecraft.roottimes.uptime.UptimeTracker uptimeTracker() {
        return uptimeTracker;
    }

    public TimesHttpServer httpServer() {
        return httpServer;
    }

    public RootRecordYamlConfig yamlConfig() {
        return yamlConfig;
    }

    public boolean corePresent() {
        return corePresent;
    }
}
