package com.rootrecord.minecraft.common;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class RootMcEnderChestResolver {

    private RootMcEnderChestResolver() {}

    public static RootMcEnderChestService resolve(Plugin plugin) {
        if (plugin == null) {
            return null;
        }
        RegisteredServiceProvider<RootMcEnderChestService> rsp =
                Bukkit.getServicesManager().getRegistration(RootMcEnderChestService.class);
        if (rsp != null && rsp.getProvider() != null) {
            return rsp.getProvider();
        }
        return ShadedServiceBridge.resolveEnderChest(plugin);
    }
}
