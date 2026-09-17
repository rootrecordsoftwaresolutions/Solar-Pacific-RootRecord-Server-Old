package com.rootrecord.minecraft.rootbonds.command;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RootBondsAdminCommand implements CommandExecutor, TabCompleter {

    private final RootBondsPlugin plugin;

    public RootBondsAdminCommand(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootbonds.reload")) {
            sender.sendMessage(color(plugin.msg("no-permission")));
            return true;
        }
        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("settle".equals(sub)) {
                sender.sendMessage(color("&eBond settlement catch-up running…"));
                plugin.bondIncome().catchUpMissedSettlements(() -> sender.sendMessage(color(
                        "&aBond settlement catch-up finished — check console for pool totals.")));
                return true;
            }
            if ("sync".equals(sub)) {
                plugin.cloudSync().syncSnapshot(true);
                sender.sendMessage(color("&aBond cloud sync queued."));
                return true;
            }
        }
        plugin.reloadLocalConfig();
        sender.sendMessage(color("&aRoot-Bonds config reloaded."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rootbonds.reload") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : List.of("settle", "sync")) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }

    private String color(String raw) {
        return raw == null ? "" : raw.replace('&', '\u00A7');
    }
}
