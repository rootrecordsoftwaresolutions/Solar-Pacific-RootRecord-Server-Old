package com.rootrecord.minecraft.rootgamble.command;

import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HiLoCommand implements CommandExecutor, TabCompleter {

    private final RootGamblePlugin plugin;

    public HiLoCommand(RootGamblePlugin plugin) {
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
            plugin.hilo().start(player, false);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help") || sub.equals("?")) {
            plugin.help().hilo(player);
            return true;
        }
        if (sub.equals("new")) {
            plugin.hilo().start(player, true);
            return true;
        }
        if (args.length < 2) {
            plugin.help().hilo(player);
            return true;
        }
        double stake;
        try {
            stake = Double.parseDouble(args[0]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-amount"));
            plugin.help().hilo(player);
            return true;
        }
        String pick = args[1].toLowerCase(Locale.ROOT);
        boolean high;
        if (pick.equals("high") || pick.equals("h") || pick.equals("up")) {
            high = true;
        } else if (pick.equals("low") || pick.equals("l") || pick.equals("down")) {
            high = false;
        } else {
            plugin.help().hilo(player);
            return true;
        }
        plugin.hilo().play(player, stake, high);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("help", "new")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
        } else if (args.length == 2) {
            String p = args[1].toLowerCase(Locale.ROOT);
            for (String s : List.of("high", "low")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
