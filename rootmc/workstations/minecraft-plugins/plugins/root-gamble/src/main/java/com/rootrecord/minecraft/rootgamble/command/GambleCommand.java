package com.rootrecord.minecraft.rootgamble.command;

import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import com.rootrecord.minecraft.rootgamble.gui.GambleMenus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GambleCommand implements CommandExecutor {

    private final RootGamblePlugin plugin;

    public GambleCommand(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootgamble.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (!plugin.config().enabled()) {
            player.sendMessage(plugin.msg("disabled"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && player.hasPermission("rootgamble.admin")) {
            plugin.reloadAll();
            player.sendMessage(plugin.msg("reload-done"));
            return true;
        }
        GambleMenus.openHub(plugin, player);
        return true;
    }
}
