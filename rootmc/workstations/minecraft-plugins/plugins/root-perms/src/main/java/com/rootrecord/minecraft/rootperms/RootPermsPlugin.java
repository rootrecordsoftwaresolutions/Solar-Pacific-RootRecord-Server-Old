package com.rootrecord.minecraft.rootperms;

import com.rootrecord.minecraft.common.RootMcPermsService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import com.rootrecord.minecraft.rootperms.command.RootPermsCommand;
import com.rootrecord.minecraft.rootperms.config.PermsConfig;
import com.rootrecord.minecraft.rootperms.data.PermsStore;
import com.rootrecord.minecraft.rootperms.listener.PermsSessionListener;
import com.rootrecord.minecraft.rootperms.service.PermsServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

public final class RootPermsPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRecordYamlConfig yaml;
    private PermsConfig config;
    private PermsStore store;
    private PermsServiceImpl perms;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        if (getServer().getPluginManager().getPlugin("Root-Core") == null) {
            var repair = RootMcCoreConnection.ensureAndRepair(this);
            getLogger().warning(
                    "Root-Core not present — connection fallback (databaseOk="
                            + repair.databaseOk()
                            + ", cloudOk="
                            + repair.cloudOk()
                            + ").");
        }

        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_PERMS_CONFIG, "root-perms.yml");
        yaml.load();
        config = PermsConfig.from(this, yaml.config());

        if (!config.mysqlReady()) {
            getLogger().severe("MySQL not configured — Root-Perms disabled until database.yml is set.");
            return;
        }

        store = new PermsStore(config);
        try {
            store.initSchema();
        } catch (Exception ex) {
            getLogger().severe("Perms schema init failed: " + ex.getMessage());
            return;
        }

        perms = new PermsServiceImpl(this, config, store);
        getServer().getServicesManager().register(RootMcPermsService.class, perms, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new PermsSessionListener(this, perms), this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            perms.refresh(online);
        }

        RootPermsCommand cmd = new RootPermsCommand(this);
        var root = getCommand("rootperms");
        if (root != null) {
            root.setExecutor(cmd);
            root.setTabCompleter(cmd);
        }

        tryRegisterVaultBridge();
        getLogger().info(
                "Root-Perms enabled — server-id="
                        + config.serverId()
                        + " default="
                        + config.defaultGroup()
                        + " groups="
                        + config.seedGroups().size());
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (perms != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                perms.clearAttachment(online);
            }
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadAll() {
        if (yaml != null) {
            yaml.reload();
        }
        config = PermsConfig.from(this, yaml.config());
        store = new PermsStore(config);
        try {
            store.initSchema();
        } catch (Exception ex) {
            getLogger().warning("Schema reload failed: " + ex.getMessage());
        }
        perms = new PermsServiceImpl(this, config, store);
        getServer().getServicesManager().unregisterAll(this);
        getServer().getServicesManager().register(RootMcPermsService.class, perms, this, ServicePriority.Normal);
        tryRegisterVaultBridge();
        for (Player online : Bukkit.getOnlinePlayers()) {
            perms.refresh(online);
        }
    }

    /**
     * Reflective load so Claims hosts (no Vault.jar) never link {@code net.milkbowl.vault.*}
     * via a hard import on this class.
     */
    private void tryRegisterVaultBridge() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault absent — Permission bridge skipped (Claims / no-Towny ok).");
            return;
        }
        try {
            Class<?> bridge = Class.forName("com.rootrecord.minecraft.rootperms.vault.VaultPermissionBridge");
            bridge.getMethod("tryRegister", org.bukkit.plugin.Plugin.class, PermsServiceImpl.class)
                    .invoke(null, this, perms);
        } catch (Throwable ex) {
            getLogger().warning("Vault Permission bridge skipped: " + ex.getMessage());
        }
    }

    /** Exposed for shaded SPI bridge. */
    public RootMcPermsService permsApi() {
        return perms;
    }

    public PermsServiceImpl perms() {
        return perms;
    }
}
