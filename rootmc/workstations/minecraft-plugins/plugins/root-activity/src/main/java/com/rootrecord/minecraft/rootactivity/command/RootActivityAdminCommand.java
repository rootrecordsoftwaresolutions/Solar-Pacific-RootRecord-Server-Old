package com.rootrecord.minecraft.rootactivity.command;

import com.rootrecord.minecraft.rootactivity.RootActivityPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class RootActivityAdminCommand implements CommandExecutor {

    private final RootActivityPlugin plugin;

    public RootActivityAdminCommand(RootActivityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootactivity.reload")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(plugin.colorize("&eUsage: /rootactivity reload"));
            return true;
        }
        plugin.reloadLocalConfig();
        sender.sendMessage(plugin.msg("reload-done"));
        return true;
    }
}
