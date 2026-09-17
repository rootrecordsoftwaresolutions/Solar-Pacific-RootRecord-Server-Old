package com.rootrecord.minecraft.rootrestart;

import com.rootrecord.minecraft.common.host.PanelHosts;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.World;

import java.lang.reflect.Method;

/** Save worlds/players then restart via Paper/Spigot restart() + restart-helper.sh. */
public final class ServerRestart {

    private ServerRestart() {}

    public static void execute(RootRestartPlugin plugin) {
        Server server = Bukkit.getServer();
        server.savePlayers();
        for (World world : server.getWorlds()) {
            world.save();
        }

        RestartScriptInstaller.ensure(plugin.host());

        Bukkit.getScheduler().runTask(plugin.host(), () -> {
            if (PanelHosts.isPterodactyl()) {
                plugin.getLogger().warning(
                        "Shockbyte/Pterodactyl owns the process — not calling Paper restart(). "
                                + "Use Panel → Restart (or a 10:00 PM HST Restart task).");
                Bukkit.broadcastMessage(ChatColor.YELLOW
                        + "[Restart] Shockbyte panel owns start/stop — Restart from the panel.");
                return;
            }
            if (invokeRestart(server, plugin)) {
                plugin.getLogger().info("Restart initiated — waiting for JVM shutdown + restart-helper.sh");
                return;
            }
            plugin.getLogger().severe(
                    "Could not call restart() — server will stop without coming back. "
                            + "Set spigot.yml restart-script to "
                            + RestartScriptInstaller.EXPECTED_SPIGOT_SCRIPT);
            server.shutdown();
        });
    }

    private static boolean invokeRestart(Server server, RootRestartPlugin plugin) {
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
}
