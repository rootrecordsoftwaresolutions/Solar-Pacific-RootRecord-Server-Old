package com.rootrecord.minecraft.rootessentials.towny;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/** Towny wilderness / town-block checks without compile-time Towny dependency. */
public final class TownyWildernessAccess {

    private TownyWildernessAccess() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("Towny") != null;
    }

    /** True when the location is not part of any town plot (Towny wilderness). */
    public static boolean isTownyWilderness(Location location) {
        if (location == null || location.getWorld() == null || !isAvailable()) {
            return false;
        }
        Object townBlock = townBlockAt(location);
        if (townBlock == null) {
            return true;
        }
        Object town = invokeNoArg(townBlock, "getTownOrNull", "getTown");
        return town == null;
    }

    /** Town name owning this location's plot, or null if wilderness / Towny unavailable. */
    public static String townNameAt(Location location) {
        if (location == null || location.getWorld() == null || !isAvailable()) {
            return null;
        }
        Object townBlock = townBlockAt(location);
        if (townBlock == null) {
            return null;
        }
        Object town = invokeNoArg(townBlock, "getTownOrNull", "getTown");
        if (town == null) {
            return null;
        }
        Object name = invokeNoArg(town, "getName");
        return name == null ? null : String.valueOf(name);
    }

    private static Object townBlockAt(Location location) {
        Object api = townyApi();
        if (api == null) {
            return null;
        }
        return invoke(api, "getTownBlock", new Class<?>[] {Location.class}, location);
    }

    private static Object townyApi() {
        try {
            Class<?> apiClass = TownyReflection.loadClass("com.palmergames.bukkit.towny.TownyAPI");
            Method method = apiClass.getMethod("getInstance");
            return method.invoke(null);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String name : methodNames) {
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
                // try next
            }
        }
        return null;
    }

    private static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable ex) {
            return null;
        }
    }
}
