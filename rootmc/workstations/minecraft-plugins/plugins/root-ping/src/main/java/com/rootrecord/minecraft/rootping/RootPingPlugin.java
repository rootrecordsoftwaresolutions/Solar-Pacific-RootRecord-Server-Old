package com.rootrecord.minecraft.rootping;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;
import com.rootrecord.minecraft.common.command.PluginCommandRegistrar;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RootPingPlugin extends JavaPlugin {

    private Metrics metrics;
    private RootRecordYamlConfig yaml;
    private PingConfig config;
    private PingSampleStore store;
    private PingSampleService service;
    private String autoHostLabel = "";

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_PING_CONFIG, "root-ping.yml");
        yaml.load();
        config = new PingConfig(yaml.config());
        autoHostLabel = detectHostLabel();

        store = new PingSampleStore(this, tablePrefix());
        store.initSchema();

        service = new PingSampleService(this);
        Bukkit.getPluginManager().registerEvents(new PingListener(this), this);

        bindPingCommand();

        if (config.enabled()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                service.startInterval(online);
            }
        }

        getLogger().info(
                "Root-Ping enabled — interval="
                        + config.intervalMinutes()
                        + "m, host="
                        + resolveHostLabel()
                        + ", mysql="
                        + store.ready());
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.cancelAll();
        }
        RootBStats.shutdown(metrics);
    }

    public void reloadAll() {
        if (service != null) {
            service.cancelAll();
        }
        yaml.load();
        config = new PingConfig(yaml.config());
        autoHostLabel = detectHostLabel();
        if (service != null) {
            service.refreshDiskRoot();
            if (config.enabled()) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    service.startInterval(online);
                }
            }
        }
        getLogger().info("Root-Ping reloaded — interval=" + config.intervalMinutes() + "m");
    }

    public PingConfig config() {
        return config;
    }

    public PingSampleStore store() {
        return store;
    }

    public PingSampleService service() {
        return service;
    }

    public String resolveServerId() {
        if (config.serverIdOverride() != null && !config.serverIdOverride().isBlank()) {
            return config.serverIdOverride();
        }
        RegisteredServiceProvider<RootCoreApi> rsp =
                Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
        if (rsp != null && rsp.getProvider() != null) {
            String id = rsp.getProvider().serverId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return "";
    }

    public String resolveHostLabel() {
        if (config.hostLabelOverride() != null && !config.hostLabelOverride().isBlank()) {
            return config.hostLabelOverride().toLowerCase();
        }
        return autoHostLabel;
    }

    private void bindPingCommand() {
        PluginCommand cmd = getCommand("ping");
        if (cmd == null) {
            cmd = PluginCommandRegistrar.register(
                    this,
                    "ping",
                    "Show your ping and current server load",
                    "/ping",
                    List.of("latency"));
        }
        if (cmd == null) {
            getLogger().severe("Could not bind /ping — command missing from plugin.yml and CommandMap fallback failed.");
            return;
        }
        PingCommand handler = new PingCommand(this);
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
        claimPrimaryLabels(cmd, "ping", "latency");
    }

    /**
     * Ensure bare /ping (and /latency) dispatch to this plugin even when another plugin
     * declared the same label earlier with a null executor.
     */
    private void claimPrimaryLabels(PluginCommand cmd, String... labels) {
        try {
            Map<String, Command> known = getServer().getCommandMap().getKnownCommands();
            for (String label : labels) {
                if (label == null || label.isBlank()) {
                    continue;
                }
                String key = label.toLowerCase(Locale.ROOT);
                Command prev = known.put(key, cmd);
                if (prev != null && prev != cmd) {
                    String owner = prev instanceof PluginCommand pc && pc.getPlugin() != null
                            ? pc.getPlugin().getName()
                            : prev.getClass().getSimpleName();
                    getLogger().info("Claimed /" + key + " (was owned by " + owner + ").");
                }
            }
        } catch (Exception ex) {
            getLogger().warning("Could not claim /ping primary labels: " + ex.getMessage());
        }
    }

    private String detectHostLabel() {
        if (Bukkit.getPluginManager().getPlugin("Towny") != null) {
            return "towny";
        }
        if (Bukkit.getPluginManager().getPlugin("Root-Claims") != null) {
            return "claims";
        }
        return "local";
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

    @SuppressWarnings("deprecation")
    public String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
