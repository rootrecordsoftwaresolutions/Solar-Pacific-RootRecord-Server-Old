package com.rootrecord.minecraft.common.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lets Root-Ops / Root-Admin host /rootrestart, and lets Root-Core / RootMC
 * request the same countdown → restart-helper path used by midnight / manual restarts.
 */
public final class ServerRestartBridge {

    @FunctionalInterface
    public interface UpdateRestartRequester {
        /** @return true if Root-Restart accepted and started a countdown */
        boolean request(String summary);
    }

    private static JavaPlugin owner;
    private static CommandExecutor executor;
    private static TabCompleter tabCompleter;
    private static UpdateRestartRequester updateRestartRequester;

    private ServerRestartBridge() {}

    public static void register(JavaPlugin plugin, CommandExecutor exec, TabCompleter tabs) {
        owner = plugin;
        executor = exec;
        tabCompleter = tabs;
    }

    public static void registerUpdateRestart(JavaPlugin plugin, UpdateRestartRequester requester) {
        if (requester == null) {
            return;
        }
        // Last writer wins so Root-Ops can attach even if Root-Restart already owns commands.
        updateRestartRequester = requester;
    }

    public static void unregister(JavaPlugin plugin) {
        if (owner != plugin) {
            return;
        }
        owner = null;
        executor = null;
        tabCompleter = null;
        updateRestartRequester = null;
    }

    public static boolean isActive() {
        return owner != null && owner.isEnabled() && executor != null;
    }

    /**
     * Starts the same Root-Restart countdown used by /rootrestart and midnight,
     * ending in Paper restart() + restart-helper.sh.
     */
    public static boolean requestPluginUpdateRestart(String summary) {
        if (updateRestartRequester == null) {
            return false;
        }
        if (owner != null && !owner.isEnabled()) {
            return false;
        }
        return updateRestartRequester.request(summary == null ? "plugin update(s)" : summary);
    }

    public static boolean dispatch(CommandSender sender, String label, String[] args) {
        if (!isActive()) {
            return false;
        }
        return executor.onCommand(sender, null, label, args);
    }

    public static java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!isActive() || tabCompleter == null) {
            return java.util.List.of();
        }
        return tabCompleter.onTabComplete(sender, null, alias, args);
    }
}
