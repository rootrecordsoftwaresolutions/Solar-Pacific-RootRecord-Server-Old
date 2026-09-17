package com.rootrecord.minecraft.rootessentials.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Permissions {

    private Permissions() {}

    public static boolean has(CommandSender sender, String node) {
        if (!(sender instanceof Player player)) {
            return sender.hasPermission("rootessentials." + node) || sender.hasPermission("essentials." + node);
        }
        return player.hasPermission("rootessentials." + node) || player.hasPermission("essentials." + node);
    }

    public static boolean hasKit(Player player, String kit) {
        return player.hasPermission("rootessentials.kit." + kit)
                || player.hasPermission("essentials.kit." + kit)
                || (has(player, "kit") && kit.equalsIgnoreCase("starter"));
    }
}
