package com.rootrecord.minecraft.rootupkeep.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class Messages {

    private Messages() {}

    public static void send(CommandSender sender, String raw) {
        sender.sendMessage(colorize(raw));
    }

    public static void broadcast(String raw) {
        String msg = colorize(raw);
        if (Bukkit.isPrimaryThread()) {
            Bukkit.broadcastMessage(msg);
            return;
        }
        org.bukkit.plugin.Plugin host = Bukkit.getPluginManager().getPlugin("Root-Upkeep");
        if (host == null) {
            host = Bukkit.getPluginManager().getPlugin("Root-Economy");
        }
        if (host != null) {
            Bukkit.getScheduler().runTask(host, () -> Bukkit.broadcastMessage(msg));
        }
    }

    public static String colorize(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
