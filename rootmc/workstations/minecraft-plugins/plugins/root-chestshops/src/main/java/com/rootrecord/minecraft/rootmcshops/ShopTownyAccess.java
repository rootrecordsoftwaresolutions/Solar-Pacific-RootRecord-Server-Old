package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/** Towny shop-plot checks via reflection (no compile-time Towny dependency). */
public final class ShopTownyAccess {

    private ShopTownyAccess() {}

    public static boolean isAvailable() {
        return plugin("Towny") != null;
    }

    public static boolean allowsShopAt(Location location, List<String> allowedPlotTypeNames) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!isAvailable()) {
            return true;
        }
        Object api = townyApi();
        if (api == null) {
            return true;
        }
        Object townBlock = invoke(api, "getTownBlock", new Class<?>[] {Location.class}, location);
        if (townBlock == null) {
            return false;
        }
        String plotType = plotTypeName(townBlock);
        if (plotType == null) {
            return false;
        }
        for (String allowed : allowedPlotTypeNames) {
            if (plotType.equalsIgnoreCase(allowed.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String plotTypeName(Object townBlock) {
        Object type = invokeNoArg(townBlock, "getType");
        if (type == null) {
            return null;
        }
        Object name = invokeNoArg(type, "getName");
        if (name == null) {
            return null;
        }
        String s = String.valueOf(name).trim();
        return s.isEmpty() ? null : s.toLowerCase(Locale.ROOT);
    }

    private static Plugin plugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    private static Object townyApi() {
        if (plugin("Towny") == null) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
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
