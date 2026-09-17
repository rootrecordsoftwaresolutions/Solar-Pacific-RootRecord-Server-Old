package com.rootrecord.minecraft.rootessentials.towny;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

/** Load Towny classes through Towny's plugin classloader. */
public final class TownyReflection {

    private TownyReflection() {}

    public static Plugin townyPlugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Towny");
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    public static ClassLoader townyLoader() {
        Plugin towny = townyPlugin();
        return towny == null ? null : towny.getClass().getClassLoader();
    }

    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader loader = townyLoader();
        if (loader == null) {
            throw new ClassNotFoundException(className);
        }
        return Class.forName(className, true, loader);
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Event> loadEventClass(String... candidates) {
        for (String name : candidates) {
            try {
                Class<?> raw = loadClass(name);
                if (Event.class.isAssignableFrom(raw)) {
                    return (Class<? extends Event>) raw;
                }
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        return null;
    }
}
