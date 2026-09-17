package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /solar — live solar mining multiplier status. */
public final class SolarCommand implements CommandExecutor {

    private final RootEconomyPlugin plugin;

    public SolarCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.solarMining() == null) {
            sender.sendMessage(plugin.colorize("&eSolar feed is offline."));
            return true;
        }
        plugin.solarMining().sendStatus(player);
        return true;
    }
}
