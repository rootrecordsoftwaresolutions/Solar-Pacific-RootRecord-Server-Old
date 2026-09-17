package com.rootrecord.minecraft.rootadmin.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;

public final class AdminPermissions {

    private AdminPermissions() {}

    public static boolean has(CommandSender sender, String node) {
        if (sender.hasPermission("rootadmin." + node)) {
            return true;
        }
        if (sender.hasPermission("essentials." + node)) {
            return true;
        }
        if (sender.hasPermission("rootessentials." + node)) {
            return true;
        }
        return !(sender instanceof Player) && sender.isOp();
    }

    /**
     * Mode-specific nodes win; blanket {@code gamemode} does not grant creative/adventure.
     * Survival is always allowed (default play mode) so join-enforce never fights permissionless players.
     */
    public static boolean canUseGamemode(CommandSender sender, GameMode mode) {
        if (mode == null || mode == GameMode.SURVIVAL) {
            return true;
        }
        String modeNode = switch (mode) {
            case SPECTATOR -> "gamemode.spectator";
            case CREATIVE -> "gamemode.creative";
            case ADVENTURE -> "gamemode.adventure";
            case SURVIVAL -> "gamemode.survival";
        };
        if (has(sender, modeNode)) {
            return true;
        }
        if (mode == GameMode.CREATIVE || mode == GameMode.ADVENTURE) {
            return false;
        }
        return has(sender, "gamemode");
    }
}
