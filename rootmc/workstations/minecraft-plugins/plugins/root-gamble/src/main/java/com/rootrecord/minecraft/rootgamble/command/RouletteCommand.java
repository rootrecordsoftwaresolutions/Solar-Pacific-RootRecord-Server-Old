package com.rootrecord.minecraft.rootgamble.command;

import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import com.rootrecord.minecraft.rootgamble.game.RouletteWheel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RouletteCommand implements CommandExecutor, TabCompleter {

    private final RootGamblePlugin plugin;

    public RouletteCommand(RootGamblePlugin plugin) {
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
        if (args.length < 2) {
            plugin.help().roulette(player);
            return true;
        }
        double stake;
        try {
            stake = Double.parseDouble(args[0]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-amount"));
            plugin.help().roulette(player);
            return true;
        }
        RouletteWheel.Bet bet = RouletteWheel.Bet.parse(args[1]);
        if (bet == null) {
            plugin.help().roulette(player);
            return true;
        }
        plugin.roulette().play(player, stake, bet);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            String p = args[1].toLowerCase(Locale.ROOT);
            for (String s : List.of("red", "black", "green", "0", "7", "17", "32")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
