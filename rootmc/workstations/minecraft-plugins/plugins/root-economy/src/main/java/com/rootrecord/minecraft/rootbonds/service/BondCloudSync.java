package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.config.BondsConfig;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;

/**
 * Compatibility facade for older callers. Bond reporting is read directly from the authoritative
 * MySQL tables through Cloudflare Hyperdrive.
 */
public final class BondCloudSync {

    private final RootBondsPlugin plugin;

    public BondCloudSync(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(
            BondsConfig config,
            BondsStore store,
            org.bukkit.configuration.file.FileConfiguration pluginConfig,
            BondPrincipalResolver principalResolver) {
        // No remote writer configuration is required.
    }

    public void syncSnapshot(boolean logResult) {
        if (logResult) {
            plugin.getLogger().info("Bond reporting is MySQL-backed; no cloud snapshot sent.");
        }
    }
}
