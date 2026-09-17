package com.rootrecord.minecraft.common;

import org.bukkit.plugin.Plugin;

public final class RootMcTreasuryResolver {

    private RootMcTreasuryResolver() {}

    public static RootMcTreasuryService resolve(Plugin plugin) {
        return ShadedServiceBridge.resolveTreasury(plugin);
    }
}
