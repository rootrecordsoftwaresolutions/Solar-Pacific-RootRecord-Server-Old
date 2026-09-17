package com.rootrecord.minecraft.common;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Resolves {@link RootMcPermsService} from ServicesManager. */
public final class RootMcPermsResolver {

    private RootMcPermsResolver() {}

    public static RootMcPermsService resolve(JavaPlugin plugin) {
        RegisteredServiceProvider<RootMcPermsService> rsp =
                Bukkit.getServicesManager().getRegistration(RootMcPermsService.class);
        if (rsp != null && rsp.getProvider() != null) {
            return rsp.getProvider();
        }
        return ShadedServiceBridge.resolvePerms(plugin);
    }
}
