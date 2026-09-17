package com.rootrecord.minecraft.rootessentials.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/** Soft-bridge to Root-Times AFK without a hard compile dependency. */
final class RootTimesAfkBridge {

    private RootTimesAfkBridge() {}

    static boolean toggle(UUID uuid) {
        Object api = api();
        if (api == null) {
            return false;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return false;
        }
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("Root-Times");
            if (plugin == null) {
                return false;
            }
            Method afkService = plugin.getClass().getMethod("afkService");
            Object service = afkService.invoke(plugin);
            if (service == null) {
                return false;
            }
            Method toggle = service.getClass().getMethod("toggle", Player.class);
            toggle.invoke(service, player);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    static Boolean isAfkOrNull(UUID uuid) {
        Object api = api();
        if (api == null) {
            return null;
        }
        try {
            Method isAfk = api.getClass().getMethod("isAfk", UUID.class);
            Object result = isAfk.invoke(api, uuid);
            return result instanceof Boolean b ? b : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static boolean isAfk(UUID uuid) {
        Boolean v = isAfkOrNull(uuid);
        return v != null && v;
    }

    static void clear(UUID uuid) {
        Object api = api();
        if (api == null) {
            return;
        }
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("Root-Times");
            if (plugin == null) {
                return;
            }
            Method afkService = plugin.getClass().getMethod("afkService");
            Object service = afkService.invoke(plugin);
            if (service == null) {
                return;
            }
            // touch clears AFK
            Method touch = service.getClass().getMethod("touch", UUID.class);
            touch.invoke(service, uuid);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
    }

    private static Object api() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Root-Times")) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("com.rootrecord.minecraft.roottimes.api.RootTimesApi");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(apiClass);
            return rsp == null ? null : rsp.getProvider();
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
}
