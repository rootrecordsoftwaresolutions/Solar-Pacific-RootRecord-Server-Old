package com.rootrecord.minecraft.rootwebstat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class WebstatCommand implements CommandExecutor, TabCompleter {

    private final RootWebstatPlugin plugin;

    public WebstatCommand(RootWebstatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootwebstat.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage("§aRoot-Webstat reloaded · port " + plugin.config().port()
                        + (plugin.http().isRunning() ? " · listening" : " · not listening"));
            }
            case "push" -> {
                plugin.refreshStats(true);
                sender.sendMessage("§aWebstat recomputed and cloud push requested.");
            }
            default -> {
                sender.sendMessage("§6Root-Webstat §7· §f" + plugin.displayServerName());
                sender.sendMessage("§7URL: §f" + plugin.http().publicUrl());
                sender.sendMessage("§7Port: §f" + plugin.config().port()
                        + " §8· day " + plugin.dayWatcher().currentDayId()
                        + " §8· vanilla 24000");
                sender.sendMessage("§7HTTP: §f" + (plugin.http().isRunning() ? "up" : "down"));
                sender.sendMessage("§8/webstat reload | status | push");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status", "push").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
