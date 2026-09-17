package com.rootrecord.minecraft.rootgamble.command;

import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class DiceCommand implements CommandExecutor, TabCompleter {

    private final RootGamblePlugin plugin;

    public DiceCommand(RootGamblePlugin plugin) {
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
            plugin.help().dice(player);
            return true;
        }
        if (args.length == 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("accept") || sub.equals("yes")) {
                plugin.dice().accept(player);
                return true;
            }
            if (sub.equals("deny") || sub.equals("no") || sub.equals("cancel")) {
                plugin.dice().deny(player);
                return true;
            }
            plugin.help().dice(player);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("pay-self"));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-amount"));
            plugin.help().dice(player);
            return true;
        }
        plugin.dice().challenge(player, target, amount);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of("accept", "deny")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            out.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .collect(Collectors.toList()));
            return out;
        }
        return List.of();
    }
}
