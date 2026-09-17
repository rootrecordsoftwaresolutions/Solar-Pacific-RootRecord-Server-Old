package com.rootrecord.minecraft.rootessentials.towny;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;

/** Resolve a player's Towny town/nation names without a compile-time Towny dependency. */
public final class TownyPlayerAccess {

    private TownyPlayerAccess() {}

    public static boolean isAvailable() {
        return plugin("Towny") != null;
    }

    public static Optional<String> townName(Player player) {
        if (player == null || !isAvailable()) {
            return Optional.empty();
        }
        Object resident = resident(player);
        if (resident == null) {
            return Optional.empty();
        }
        Object town = invokeNoArg(resident, "getTownOrNull", "getTown");
        if (town == null) {
            return Optional.empty();
        }
        Object name = invokeNoArg(town, "getName");
        return optionalName(name);
    }

    public static Optional<String> nationName(Player player) {
        if (player == null || !isAvailable()) {
            return Optional.empty();
        }
        Object resident = resident(player);
        if (resident == null) {
            return Optional.empty();
        }
        Object town = invokeNoArg(resident, "getTownOrNull", "getTown");
        if (town == null) {
            return Optional.empty();
        }
        Object nation = invokeNoArg(town, "getNationOrNull", "getNation");
        if (nation == null) {
            return Optional.empty();
        }
        Object name = invokeNoArg(nation, "getName");
        return optionalName(name);
    }

    private static Object resident(Player player) {
        Object api = townyApi();
        if (api == null) {
            return null;
        }
        return invoke(api, "getResident", new Class<?>[] {Player.class}, player);
    }

    private static Optional<String> optionalName(Object name) {
        if (name == null) {
            return Optional.empty();
        }
        String s = String.valueOf(name).trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
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
