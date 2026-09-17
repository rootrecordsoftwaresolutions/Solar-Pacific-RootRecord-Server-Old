package com.rootrecord.minecraft.rootcore.update;

import com.rootrecord.minecraft.common.command.ServerRestartBridge;
import com.rootrecord.minecraft.common.host.PanelHosts;
import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * After jar downloads: prefer Root-Restart countdown (same as midnight / /rootrestart),
 * which ends in Paper restart() + restart-helper.sh.
 */
final class SuiteRestart {

    private static final AtomicBoolean PENDING = new AtomicBoolean(false);

    private SuiteRestart() {}

    static void scheduleAfterUpdates(RootCorePlugin plugin, int delaySeconds, List<String> notes) {
        if (!PENDING.compareAndSet(false, true)) {
            plugin.getLogger().info("Suite restart already scheduled — skipping duplicate");
            return;
        }
        String summary = notes == null || notes.isEmpty() ? "plugin update(s)" : String.join(", ", notes);
        plugin.getLogger().info("Root-Core updater: requesting restart after: " + summary);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ServerRestartBridge.requestPluginUpdateRestart(summary)) {
                plugin.getLogger().info(
                        "Root-Core updater: handed off to Root-Restart (same path as midnight)");
                // Root-Restart owns the countdown; allow another request later if needed.
                PENDING.set(false);
                return;
            }
            if (PanelHosts.isPterodactyl()) {
                plugin.getLogger().warning(
                        "Root-Restart unavailable on Shockbyte — jars are on disk. "
                                + "Not shutting down (Paper stop does not come back). "
                                + "Start/Restart from the Shockbyte panel.");
                PENDING.set(false);
                return;
            }
            // Fallback when Root-Ops/Root-Restart is missing
            int delay = Math.max(5, delaySeconds);
            broadcast("&eRoot-Core updated plugins (&f" + summary + "&e). Restarting in &f" + delay + "s&e…");
            int[] left = {delay};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                int sec = left[0];
                if (sec <= 0) {
                    task.cancel();
                    executeFallback(plugin);
                    return;
                }
                if (sec <= 10 || sec % 10 == 0) {
                    broadcast("&eServer restarting in &f" + sec + "s &e(plugin updates).");
                }
                left[0] = sec - 1;
            }, 20L, 20L);
        });
    }

    private static void executeFallback(RootCorePlugin plugin) {
        broadcast("&c&lRestarting now to apply plugin updates!");
        Server server = Bukkit.getServer();
        try {
            server.savePlayers();
            for (World world : server.getWorlds()) {
                world.save();
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Save before suite restart failed", ex);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (PanelHosts.isPterodactyl()) {
                plugin.getLogger().severe(
                        "Skipping Paper restart on Shockbyte — that leaves the panel server offline. "
                                + "Jars are on disk. Restart from the Shockbyte panel.");
                PENDING.set(false);
                return;
            }
            if (invokeRestart(server, plugin)) {
                plugin.getLogger().info("Suite restart initiated (Paper restart / restart-script)");
                return;
            }
            plugin.getLogger().severe(
                    "restart() unavailable — shutting down. Ensure spigot.yml restart-script is set "
                            + "(Root-Restart installs ./plugins/RootMC/restart-helper.sh).");
            PENDING.set(false);
            server.shutdown();
        });
    }

    private static boolean invokeRestart(Server server, RootCorePlugin plugin) {
        try {
            Object spigot = server.getClass().getMethod("spigot").invoke(server);
            Method restart = spigot.getClass().getMethod("restart");
            restart.invoke(spigot);
            return true;
        } catch (ReflectiveOperationException spigotEx) {
            try {
                Method restart = server.getClass().getMethod("restart");
                restart.invoke(server);
                return true;
            } catch (ReflectiveOperationException serverEx) {
                plugin.getLogger().warning(
                        "restart() unavailable: " + serverEx.getClass().getSimpleName()
                                + " — " + serverEx.getMessage());
                return false;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void broadcast(String colored) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', colored));
    }
}
