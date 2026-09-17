package com.rootrecord.minecraft.rootterritories.towny;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    public static Object invoke(Object target, String methodName, Object arg) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (!param.isInstance(arg)) {
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

    static Plugin plugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    public static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    public static int intOrZero(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        Plugin towny = plugin("Towny");
        if (towny == null) {
            throw new ClassNotFoundException(className);
        }
        return Class.forName(className, true, towny.getClass().getClassLoader());
    }

    public static void cancelEvent(Object event, String message) {
        if (event == null) {
            return;
        }
        try {
            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
        } catch (Throwable ignored) {
            return;
        }
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            event.getClass().getMethod("setCancelMessage", String.class).invoke(event, message);
        } catch (Throwable ignored) {
            // optional on older Towny builds
        }
    }
}
