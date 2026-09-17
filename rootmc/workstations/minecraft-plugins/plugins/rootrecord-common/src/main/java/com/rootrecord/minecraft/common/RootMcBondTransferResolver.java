package com.rootrecord.minecraft.common;

import org.bukkit.plugin.Plugin;

public final class RootMcBondTransferResolver {

    private RootMcBondTransferResolver() {}

    public static RootMcBondTransferService resolve(Plugin plugin) {
        return ShadedServiceBridge.resolveBondTransfer(plugin);
    }
}
