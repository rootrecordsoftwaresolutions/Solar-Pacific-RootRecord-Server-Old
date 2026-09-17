package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminWarpCommands {

    private AdminWarpCommands() {}

    public static final class SetWarp implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public SetWarp(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "setwarp")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /setwarp <name>")); return true; }
            try {
                plugin.warps().upsert(args[0], player.getLocation());
                player.sendMessage(plugin.colorize("&aWarp &f" + args[0] + " &aset."));
            } catch (Exception ex) {
                player.sendMessage(plugin.colorize("&cSetwarp failed: &f" + ex.getMessage()));
            }
            return true;
        }
    }

    public static final class DelWarp implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public DelWarp(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "delwarp")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { sender.sendMessage(plugin.colorize("&eUsage: /delwarp <name>")); return true; }
            try {
                if (!plugin.warps().delete(args[0])) {
                    sender.sendMessage(plugin.colorize("&eWarp &f" + args[0] + " &enot found."));
                    return true;
                }
                sender.sendMessage(plugin.colorize("&aDeleted warp &f" + args[0] + "&a."));
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&cDelwarp failed: &f" + ex.getMessage()));
            }
            return true;
        }
    }
}
