package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class WorldBorderCommand implements CommandExecutor {

    private final RootAdminPlugin plugin;

    public WorldBorderCommand(RootAdminPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!AdminPermissions.has(sender, "worldborder")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.applyWorldBorder();
        sender.sendMessage(plugin.msg("world-border-applied"));
        return true;
    }
}
