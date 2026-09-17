package com.rootrecord.minecraft.rootcore.api;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootMcDiscordConfig;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import com.rootrecord.minecraft.rootcore.license.LicenseGate;
import com.rootrecord.minecraft.rootcore.license.LicenseStatus;
import org.bukkit.configuration.file.FileConfiguration;

public final class RootCoreApiImpl implements RootCoreApi {

    private final RootCorePlugin plugin;
    private final LicenseGate licenseGate;
    private volatile boolean ready;
    private volatile RootMcDatabaseConfig.DatabaseSettings databaseSettings;
    private volatile RootRecordCloudConfig.CloudSettings cloudSettings;
    private volatile RootMcDiscordConfig.DiscordSettings discordSettings;

    public RootCoreApiImpl(RootCorePlugin plugin, LicenseGate licenseGate) {
        this.plugin = plugin;
        this.licenseGate = licenseGate;
        refresh();
    }

    public void refresh() {
        // Prefer root-core.yml cloud.* then shared cloud.yml (same as license bind).
        FileConfiguration rootCore = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        databaseSettings = RootMcDatabaseConfig.resolve(plugin, null);
        cloudSettings = RootRecordCloudConfig.resolve(plugin, rootCore);
        discordSettings = RootMcDiscordConfig.resolve(plugin);
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public RootMcDatabaseConfig.DatabaseSettings databaseSettings() {
        return databaseSettings;
    }

    @Override
    public String apiBase() {
        return cloudSettings != null
                ? cloudSettings.apiBase()
                : com.rootrecord.minecraft.common.config.RootMcApiBases.defaultBase();
    }

    @Override
    public String serverId() {
        return cloudSettings != null ? cloudSettings.serverId() : "";
    }

    @Override
    public String serverName() {
        if (plugin.licenseConnect() != null) {
            String name = plugin.licenseConnect().serverName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        if (plugin.yamlConfig() != null) {
            String fromYml = plugin.yamlConfig().config().getString("server-name", "");
            if (fromYml != null && !fromYml.isBlank()) {
                return fromYml.trim();
            }
        }
        return "Server";
    }

    @Override
    public boolean hasCloudCredentials() {
        return cloudSettings != null && cloudSettings.hasServerCredentials();
    }

    @Override
    public RootMcDiscordConfig.DiscordSettings discordSettings() {
        return discordSettings != null ? discordSettings : RootMcDiscordConfig.empty();
    }

    @Override
    public LicenseStatus licenseStatus(String pluginId) {
        return licenseGate.status(pluginId);
    }

    @Override
    public boolean isLicensed(String pluginId) {
        return licenseGate.isLicensed(pluginId);
    }

    @Override
    public void ensureCoreFiles() {
        RootMcCoreConnection.RepairResult result = RootMcCoreConnection.ensureAndRepair(plugin);
        refresh();
        setReady(result.ok());
    }
}
