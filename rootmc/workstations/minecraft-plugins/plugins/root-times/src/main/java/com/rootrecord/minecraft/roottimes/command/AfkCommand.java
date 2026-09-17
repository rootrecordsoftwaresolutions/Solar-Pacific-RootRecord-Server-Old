package com.rootrecord.minecraft.roottimes.command;

import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("deprecation")
public final class AfkCommand implements CommandExecutor {

    private final RootTimesPlugin plugin;

    public AfkCommand(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("roottimes.afk")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (plugin.afkService() == null) {
            player.sendMessage(ChatColor.RED + "AFK is disabled.");
            return true;
        }
        // If Root-Essentials owns /afk, this command may not bind; when it does, toggle Times AFK.
        plugin.afkService().toggle(player);
        return true;
    }
}
