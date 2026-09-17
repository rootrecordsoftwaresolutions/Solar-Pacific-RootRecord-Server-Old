package com.rootrecord.minecraft.rootcore;

import com.rootrecord.minecraft.common.FancyUiConfig;
import com.rootrecord.minecraft.common.RootDiscordApi;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import com.rootrecord.minecraft.rootcore.api.RootCoreApiImpl;
import com.rootrecord.minecraft.rootcore.cloud.CloudConfigEnsure;
import com.rootrecord.minecraft.rootcore.command.GotoCommand;
import com.rootrecord.minecraft.rootcore.command.RootCoreCommand;
import com.rootrecord.minecraft.rootcore.comms.CommsConfig;
import com.rootrecord.minecraft.rootcore.comms.DiscordChatBridge;
import com.rootrecord.minecraft.rootcore.comms.RootCommsApiImpl;
import com.rootrecord.minecraft.rootcore.license.LicenseConnectService;
import com.rootrecord.minecraft.rootcore.license.LicenseGate;
import com.rootrecord.minecraft.rootcore.transfer.TransferMeshService;
import com.rootrecord.minecraft.rootcore.update.CorePluginUpdater;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

public final class RootCorePlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRecordYamlConfig yamlConfig;
    private LicenseGate licenseGate;
    private LicenseConnectService licenseConnect;
    private CorePluginUpdater updater;
    private RootCoreApiImpl api;
    private TransferMeshService transferMesh;
    private DiscordChatBridge commsBridge;
    private RootCommsApiImpl commsApi;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        yamlConfig = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_CORE_CONFIG, "root-core.yml");
        yamlConfig.load();
        FancyUiConfig.load(this);

        licenseGate = new LicenseGate(this);
        licenseGate.reload(yamlConfig.config());

        RootMcCoreConnection.RepairResult repair = CloudConfigEnsure.ensureAll(this);
        CloudConfigEnsure.ensureLicenseScaffold(this);

        api = new RootCoreApiImpl(this, licenseGate);
        api.refresh();
        api.setReady(repair.ok());

        getServer().getServicesManager().register(RootCoreApi.class, api, this, ServicePriority.Normal);

        commsApi = new RootCommsApiImpl(this);
        getServer().getServicesManager().register(RootDiscordApi.class, commsApi, this, ServicePriority.Normal);
        startComms();

        RootCoreCommand command = new RootCoreCommand(this);
        var rootcore = getCommand("rootcore");
        if (rootcore != null) {
            rootcore.setExecutor(command);
            rootcore.setTabCompleter(command);
        }

        licenseConnect = new LicenseConnectService(this);
        licenseConnect.startOrReload();

        transferMesh = new TransferMeshService(this);
        transferMesh.startOrReload();

        GotoCommand gotoCommand = new GotoCommand(this);
        var gotoCmd = getCommand("goto");
        if (gotoCmd != null) {
            gotoCmd.setExecutor(gotoCommand);
            gotoCmd.setTabCompleter(gotoCommand);
        }

        updater = new CorePluginUpdater(this);
        updater.startOrReload();

        warnTownyWithoutVault();

        getLogger().info(
                "Root-Core enabled — plugins/"
                        + RootRecordFolders.FOLDER_NAME
                        + "/ ready="
                        + api.isReady()
                        + " license="
                        + licenseGate.mode().name().toLowerCase()
                        + " comms="
                        + (commsBridge != null && commsBridge.isReady()));
    }

    /** Towny needs Vault.jar on the host; Claims-only hosts skip this check. */
    public void warnTownyWithoutVault() {
        PluginManager pm = Bukkit.getPluginManager();
        if (pm.getPlugin("Towny") == null) {
            return;
        }
        if (pm.getPlugin("Vault") != null) {
            return;
        }
        getLogger().severe(
                "Towny is installed but Vault is missing — Towny requires Vault "
                        + "(economy + optional permission bridge via Root-Perms).");
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (commsBridge != null) {
            commsBridge.stop();
            commsBridge = null;
        }
        if (transferMesh != null) {
            transferMesh.stop();
        }
        if (updater != null) {
            updater.stop();
        }
        if (licenseConnect != null) {
            licenseConnect.stop();
        }
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("Root-Core disabled — ServicesManager API unregistered.");
    }

    /** Reload root-core.yml, re-ensure connection files, refresh API snapshot, rebind license. */
    public void reloadCore() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        FancyUiConfig.load(this);
        if (licenseGate != null && yamlConfig != null) {
            licenseGate.reload(yamlConfig.config());
        }
        boolean repairOnReload = yamlConfig == null
                || yamlConfig.config().getBoolean("repair.on-reload", true);
        if (repairOnReload && api != null) {
            api.ensureCoreFiles();
        } else if (api != null) {
            api.refresh();
        }
        CloudConfigEnsure.ensureLicenseScaffold(this);
        if (licenseConnect != null) {
            licenseConnect.startOrReload();
        }
        if (updater != null) {
            updater.startOrReload();
        }
        if (transferMesh != null) {
            transferMesh.startOrReload();
        }
        reloadComms();
    }

    /** Reflective entry for Root-Restart evacuate-before-stop. */
    public void evacuateForRestart(Runnable after) {
        if (transferMesh != null) {
            transferMesh.evacuateForRestart(after);
            return;
        }
        if (after != null) {
            after.run();
        }
    }

    public TransferMeshService transferMesh() {
        return transferMesh;
    }

    public RootCoreApi api() {
        return api;
    }

    public LicenseGate licenseGate() {
        return licenseGate;
    }

    public LicenseConnectService licenseConnect() {
        return licenseConnect;
    }

    public CorePluginUpdater updater() {
        return updater;
    }

    public RootRecordYamlConfig yamlConfig() {
        return yamlConfig;
    }

    /** Discord/Slack bridge — reflective resolve via {@code commsApi()}. */
    public DiscordChatBridge commsBridge() {
        return commsBridge;
    }

    /** RootDiscordApi for shaded consumers (RootMC / Official). */
    public RootDiscordApi commsApi() {
        return commsApi;
    }

    public void reloadComms() {
        startComms();
        getLogger().info(
                "Root-Core comms reloaded — ready=" + (commsBridge != null && commsBridge.isReady()));
    }

    private void startComms() {
        if (commsBridge != null) {
            commsBridge.stop();
            commsBridge = null;
        }
        Plugin legacy = Bukkit.getPluginManager().getPlugin("Root-Discord");
        if (legacy != null) {
            getLogger().severe(
                    "Legacy Root-Discord is still installed — Core comms disabled to avoid a double JDA "
                            + "session. Remove root-discord-*.jar and restart.");
            return;
        }
        if (yamlConfig != null) {
            CommsConfig.migrateLegacyDiscordYaml(this, yamlConfig.config());
            yamlConfig.reload();
        }
        CommsConfig config = CommsConfig.from(this, yamlConfig != null ? yamlConfig.config() : null);
        commsBridge = new DiscordChatBridge(this, config);
        commsBridge.start();
    }
}
