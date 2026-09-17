package com.rootrecord.minecraft.roothelp.command;

import com.rootrecord.minecraft.common.RootMcMapUrls;
import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class MapCommand implements CommandExecutor {

    private final RootHelpPlugin plugin;

    public MapCommand(RootHelpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roothelp.map")) {
            sender.sendMessage(plugin.colorize("&cYou don't have permission."));
            return true;
        }

        RootMcMapUrls.sendOpenMapMessage(
                sender,
                plugin,
                plugin.helpConfig().mapBaseUrl(),
                plugin.msg("map-header"),
                plugin::colorize);
        return true;
    }
}
