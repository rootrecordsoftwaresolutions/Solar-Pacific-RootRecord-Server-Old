package com.rootrecord.minecraft.rootgamble.command;

import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class RootGambleAdminCommand implements CommandExecutor {

    private final RootGamblePlugin plugin;

    public RootGambleAdminCommand(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootgamble.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(plugin.msg("reload-done"));
            return true;
        }
        sender.sendMessage(plugin.colorize("&eUsage: &f/rootgamble reload"));
        return true;
    }
}
