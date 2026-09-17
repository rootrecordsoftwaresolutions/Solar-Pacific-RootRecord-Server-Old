package com.rootrecord.minecraft.rootappreciation;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BonusCommand implements CommandExecutor {

    private final RootAppreciationPlugin plugin;

    public BonusCommand(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!plugin.config().enabled()) {
            player.sendMessage(plugin.msg("disabled"));
            return true;
        }
        if (!player.hasPermission("rootappreciation.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.service().claimBonus(player);
        return true;
    }
}
