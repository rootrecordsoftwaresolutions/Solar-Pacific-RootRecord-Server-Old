package com.rootrecord.minecraft.rootloans.town;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Minimal Towny reflection for mayor / town checks. */
public final class TownyAccess {

    private TownyAccess() {}

    public static boolean isAvailable() {
        Plugin towny = Bukkit.getPluginManager().getPlugin("Towny");
        return towny != null && towny.isEnabled();
    }

    public static Optional<String> playerTownName(Player player) {
        if (player == null || !isAvailable()) {
            return Optional.empty();
        }
        Object resident = resident(player);
        if (resident == null) {
            return Optional.empty();
        }
        Object town = invokeNoArg(resident, "getTownOrNull", "getTown");
        return Optional.ofNullable(stringOrNull(invokeNoArg(town, "getName")));
    }

    public static boolean isMayorOf(Player player, String townName) {
        if (player == null || townName == null || townName.isBlank() || !isAvailable()) {
            return false;
        }
        Object town = townByName(townName);
        if (town == null) {
            return false;
        }
        Object mayor = invokeNoArg(town, "getMayor");
        if (mayor == null) {
            return false;
        }
        Object uuid = invokeNoArg(mayor, "getUUID", "getUniqueId");
        if (uuid instanceof UUID id) {
            return id.equals(player.getUniqueId());
        }
        String name = stringOrNull(invokeNoArg(mayor, "getName"));
        return name != null && name.equalsIgnoreCase(player.getName());
    }

    public static String townBankAccountName(String townName) {
        return "town-" + townName;
    }

    private static Object townByName(String townName) {
        Object api = townyApi();
        if (api == null) {
            return null;
        }
        return invoke(api, "getTown", townName);
    }

    private static Object resident(Player player) {
        Object api = townyApi();
        if (api == null) {
            return null;
        }
        return invoke(api, "getResident", player);
    }

    private static Object townyApi() {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Method method = apiClass.getMethod("getInstance");
            return method.invoke(null);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Object arg) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                if (!method.getParameterTypes()[0].isInstance(arg)) {
                    continue;
                }
                method.setAccessible(true);
                return method.invoke(target, arg);
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return null;
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

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
