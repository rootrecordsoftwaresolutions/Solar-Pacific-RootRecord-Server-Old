package com.rootrecord.minecraft.common.command;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.logging.Level;

public final class PluginCommandRegistrar {

    private PluginCommandRegistrar() {}

    public static PluginCommand register(JavaPlugin plugin, String name, String description, String usage,
            List<String> aliases) {
        PluginCommand existing = plugin.getCommand(name);
        if (existing != null) {
            return existing;
        }
        try {
            Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            constructor.setAccessible(true);
            PluginCommand cmd = constructor.newInstance(name, plugin);
            cmd.setDescription(description);
            cmd.setUsage(usage);
            if (aliases != null && !aliases.isEmpty()) {
                cmd.setAliases(aliases);
            }
            plugin.getServer().getCommandMap().register(plugin.getName().toLowerCase(java.util.Locale.ROOT), cmd);
            plugin.getLogger().info("Registered /" + name + " via CommandMap fallback.");
            return cmd;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not register /" + name + " via CommandMap", ex);
            return null;
        }
    }
}
