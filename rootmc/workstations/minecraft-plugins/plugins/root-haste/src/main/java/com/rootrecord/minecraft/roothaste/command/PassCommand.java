package com.rootrecord.minecraft.roothaste.command;

import com.rootrecord.minecraft.roothaste.RootHastePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PassCommand implements CommandExecutor, TabCompleter {

    private final RootHastePlugin plugin;

    public PassCommand(RootHastePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.msg("usage-pass"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
            return true;
        }
        String result = plugin.sessions().tryPass(player, target);
        if (result != null) {
            if ("player-not-found".equals(result)) {
                player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
            } else {
                player.sendMessage(plugin.msg(result));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player self)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(self.getUniqueId())) {
                continue;
            }
            String name = online.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }
}
