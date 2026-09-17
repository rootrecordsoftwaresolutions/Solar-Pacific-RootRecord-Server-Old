package com.rootrecord.minecraft.rootrewards.command;

import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class RootRewardsAdminCommand implements CommandExecutor {

    private final RootRewardsPlugin plugin;

    public RootRewardsAdminCommand(RootRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootrewards.reload")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(plugin.colorize("&eUsage: /rootrewards reload"));
            return true;
        }
        plugin.reloadLocalConfig();
        plugin.restartScheduledTasks();
        sender.sendMessage(plugin.msg("reload-done"));
        return true;
    }
}
