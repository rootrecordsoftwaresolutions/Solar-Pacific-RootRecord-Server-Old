package com.rootrecord.minecraft.common;

import org.bukkit.plugin.Plugin;

public final class RootMcLoanResolver {

    private RootMcLoanResolver() {}

    public static RootMcLoanService resolve(Plugin plugin) {
        return ShadedServiceBridge.resolveLoans(plugin);
    }
}
