package com.rootrecord.minecraft.rootrestart;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;

/** Save worlds/players then shut down without invoking the restart script (panel update / stop). */
public final class ServerStop {

    private ServerStop() {}

    public static void execute(RootRestartPlugin plugin) {
        Server server = Bukkit.getServer();
        server.savePlayers();
        for (World world : server.getWorlds()) {
            world.save();
        }
        Bukkit.getScheduler().runTask(plugin.host(), () -> {
            plugin.getLogger().info("Stop-for-update initiated — shutting down (no restart-helper).");
            server.shutdown();
        });
    }
}
