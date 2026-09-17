package com.rootrecord.minecraft.rootgamble.command;

import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LottoCommand implements CommandExecutor {

    private final RootGamblePlugin plugin;

    public LottoCommand(RootGamblePlugin plugin) {
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
        if (args.length == 0) {
            plugin.lotto().status(player);
            return true;
        }
        int number;
        try {
            number = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            plugin.help().lotto(player);
            return true;
        }
        plugin.lotto().buy(player, number);
        return true;
    }
}
