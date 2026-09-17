package com.rootrecord.minecraft.rootranks.command;

import com.rootrecord.minecraft.rootranks.RootRanksPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class RootRanksAdminCommand implements CommandExecutor {

    private final RootRanksPlugin plugin;

    public RootRanksAdminCommand(RootRanksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootranks.reload")) {
            sender.sendMessage(plugin.msg(plugin.ranksConfig().noPermission()));
            return true;
        }
        if (args.length == 0 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(plugin.colorize("&eUsage: /rootranks reload"));
            return true;
        }
        plugin.reloadLocalConfig();
        sender.sendMessage(plugin.msg(plugin.ranksConfig().reloadDone()));
        return true;
    }
}
