package com.rootrecord.minecraft.rootterritories.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class TerritoryMessages {

    private TerritoryMessages() {}

    public static void sendBorderAlert(Player player, String body) {
        player.sendMessage(colorize(body));
    }

    public static void sendLine(Player player, String raw) {
        player.sendMessage(colorize(raw));
    }

    public static String colorize(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
