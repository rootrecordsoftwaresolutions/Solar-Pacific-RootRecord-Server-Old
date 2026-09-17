package com.rootrecord.minecraft.rootbonds.towny;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class TownyReflection {

    private TownyReflection() {}

    public static boolean isAvailable() {
        return plugin("Towny") != null;
    }

    public static Object townyApi() {
        if (!isAvailable()) {
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

    public static Object invokeNoArg(Object target, String... methodNames) {
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

    public static Collection<?> asCollection(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object item : iterable) {
                list.add(item);
            }
            return list;
        }
        return List.of(value);
    }

    public static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    public static UUID uuidOrNull(Object resident) {
        Object value = invokeNoArg(resident, "getUUID", "getUniqueId");
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value != null) {
            try {
                return UUID.fromString(String.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    public static UUID accountUuid(Object account) {
        return uuidOrNull(account);
    }

    public static String accountName(Object account) {
        return stringOrNull(invokeNoArg(account, "getName"));
    }

    public static Object townByName(String townName) {
        Object api = townyApi();
        if (api == null || townName == null || townName.isBlank()) {
            return null;
        }
        return invoke(api, "getTown", townName.trim());
    }

    public static Object nationByName(String nationName) {
        Object api = townyApi();
        if (api == null || nationName == null || nationName.isBlank()) {
            return null;
        }
        return invoke(api, "getNation", nationName.trim());
    }

    public static Object resident(org.bukkit.entity.Player player) {
        Object api = townyApi();
        if (api == null || player == null) {
            return null;
        }
        Object resident = invoke(api, "getResident", player.getUniqueId());
        if (resident == null) {
            resident = invoke(api, "getResident", player);
        }
        if (resident == null && player.getName() != null) {
            resident = invoke(api, "getResident", player.getName());
        }
        return resident;
    }

    private static Object invoke(Object target, String methodName, Object arg) {
        if (target == null || methodName == null || arg == null) {
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
            return null;
        }
        return null;
    }

    private static Plugin plugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }
}
