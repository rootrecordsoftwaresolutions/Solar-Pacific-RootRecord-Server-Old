package com.rootrecord.minecraft.rootcore.cloud;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ensures {@code plugins/RootMC/cloud.yml} (and the rest of the connection unit) via
 * {@link RootMcCoreConnection}. Prefer calling {@link RootMcCoreConnection#ensureAndRepair}
 * directly from feature plugins when Root-Core is absent.
 */
public final class CloudConfigEnsure {

    private CloudConfigEnsure() {}

    /** Full connection unit repair (database + cloud + meta). */
    public static RootMcCoreConnection.RepairResult ensureAll(JavaPlugin plugin) {
        return RootMcCoreConnection.ensureAndRepair(plugin);
    }

    /** Cloud file only via legacy helper (create-if-missing; no merge). Prefer {@link #ensureAll}. */
    public static void ensureCloudStub(JavaPlugin plugin) {
        RootRecordCloudConfig.ensureDefaults(plugin);
    }

    /** Optional license.yml scaffold under RootMC/. */
    public static void ensureLicenseScaffold(JavaPlugin plugin) {
        new RootRecordYamlConfig(plugin, RootRecordFolders.LICENSE_CONFIG, "license.yml").load();
    }
}
