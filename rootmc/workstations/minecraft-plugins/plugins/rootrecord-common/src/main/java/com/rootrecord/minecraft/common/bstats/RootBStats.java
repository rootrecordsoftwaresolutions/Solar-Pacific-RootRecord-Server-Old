package com.rootrecord.minecraft.common.bstats;

import org.bukkit.plugin.java.JavaPlugin;

/** Starts bStats when a plugin has a registered service id; no-ops otherwise. */
public final class RootBStats {

    private RootBStats() {}

    public static Metrics start(JavaPlugin plugin) {
        return start(plugin, BStatsIds.forPlugin(plugin));
    }

    public static Metrics start(JavaPlugin plugin, int pluginId) {
        if (plugin == null || !BStatsIds.isRegistered(pluginId)) {
            return null;
        }
        try {
            return new Metrics(plugin, pluginId);
        } catch (Throwable ex) {
            plugin.getLogger().warning("bStats Metrics failed to start: " + ex.getMessage());
            return null;
        }
    }

    public static void shutdown(Metrics metrics) {
        if (metrics == null) {
            return;
        }
        try {
            metrics.shutdown();
        } catch (Throwable ignored) {
            // ignore
        }
    }
}
