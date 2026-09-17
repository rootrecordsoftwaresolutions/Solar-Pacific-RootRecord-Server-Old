package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RenameHomeCommand implements CommandExecutor {

    private final RootEssentialsPlugin plugin;

    public RenameHomeCommand(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Permissions.has(player, "renamehome")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: /renamehome <old> <new>"));
            return true;
        }
        try {
            boolean ok = plugin.renameHome(player.getUniqueId(), args[0], args[1]);
            if (!ok) {
                player.sendMessage(plugin.msg("home-missing").replace("{name}", args[0]));
                return true;
            }
            player.sendMessage(plugin.colorize("&aRenamed home &f" + args[0] + " &a→ &f" + args[1]));
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cRename failed: &f" + ex.getMessage()));
        }
        return true;
    }
}
