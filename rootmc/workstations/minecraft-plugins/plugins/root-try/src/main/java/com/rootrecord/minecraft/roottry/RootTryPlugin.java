package com.rootrecord.minecraft.roottry;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

public final class RootTryPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRecordYamlConfig yaml;
    private TryConfig tryConfig;
    private TryCatalog catalog;
    private TryCompletionStore store;
    private TryService service;
    private TryReminderService reminder;
    private String activeHost = "both";

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_TRY_CONFIG, "root-try.yml");
        yaml.load();
        tryConfig = new TryConfig(yaml.config());
        activeHost = resolveHost(tryConfig.hostOverride());
        catalog = TryCatalog.load(this, tryConfig.rewardG());

        String prefix = tablePrefix();
        store = new TryCompletionStore(this, prefix);
        store.initSchema();

        service = new TryService(this, tryConfig, catalog, store, activeHost);
        reminder = new TryReminderService(this);

        Bukkit.getPluginManager().registerEvents(new TryCommandListener(this), this);

        PluginCommand cmd = getCommand("try");
        if (cmd != null) {
            TryCommand handler = new TryCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        if (tryConfig.enabled()) {
            reminder.start();
        }
        getLogger().info("Root-Try enabled — host=" + activeHost + ", tries=" + catalog.forHost(activeHost).size());
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (reminder != null) {
            reminder.stop();
        }
    }

    public void reloadAll() {
        if (reminder != null) {
            reminder.stop();
        }
        yaml.load();
        tryConfig = new TryConfig(yaml.config());
        activeHost = resolveHost(tryConfig.hostOverride());
        catalog = TryCatalog.load(this, tryConfig.rewardG());
        service = new TryService(this, tryConfig, catalog, store, activeHost);
        reminder = new TryReminderService(this);
        if (tryConfig.enabled()) {
            reminder.start();
        }
        getLogger().info("Root-Try reloaded — host=" + activeHost + ", tries=" + catalog.forHost(activeHost).size());
    }

    public TryService service() {
        return service;
    }

    public String activeHost() {
        return activeHost;
    }

    private String resolveHost(String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase();
        }
        if (Bukkit.getPluginManager().getPlugin("Towny") != null) {
            return "towny";
        }
        if (Bukkit.getPluginManager().getPlugin("Root-Claims") != null) {
            return "claims";
        }
        return "both";
    }

    private String tablePrefix() {
        RegisteredServiceProvider<RootCoreApi> rsp =
                Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
        if (rsp != null && rsp.getProvider() != null) {
            var db = rsp.getProvider().databaseSettings();
            if (db != null && db.tablePrefix() != null && !db.tablePrefix().isBlank()) {
                return db.tablePrefix();
            }
        }
        return "root_";
    }
}
