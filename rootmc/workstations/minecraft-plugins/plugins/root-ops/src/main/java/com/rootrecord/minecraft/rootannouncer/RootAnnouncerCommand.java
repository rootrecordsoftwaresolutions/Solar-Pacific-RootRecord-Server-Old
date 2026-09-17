package com.rootrecord.minecraft.rootannouncer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class RootAnnouncerCommand implements CommandExecutor, TabCompleter {

    private final RootAnnouncerPlugin plugin;

    public RootAnnouncerCommand(RootAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.colorize("&eUsage: /rootannouncer <reload|list|now> [index]"));
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "now", "broadcast" -> handleNow(sender, args);
            default -> {
                sender.sendMessage(plugin.colorize("&eUsage: /rootannouncer <reload|list|now> [index]"));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rootannouncer.reload")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.reloadLocalConfig();
        plugin.restartTask();
        sender.sendMessage(plugin.msg("reload-done"));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("rootannouncer.reload")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        AnnouncerConfig config = plugin.config();
        sender.sendMessage(plugin.colorize(String.format(
                Locale.ROOT,
                "&7Announcer: &f%d &7local + &f%d &7effective, every &f%d &7s, &f%s&7.",
                config.lines().size(),
                plugin.effectiveLines().size(),
                config.intervalSeconds(),
                config.randomOrder() ? "random" : "sequential")));
        for (int i = 0; i < plugin.effectiveLines().size(); i++) {
            sender.sendMessage(plugin.colorize("&8" + i + "&7: " + plugin.effectiveLines().get(i)));
        }
        return true;
    }

    private boolean handleNow(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootannouncer.now")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        int index = -1;
        if (args.length >= 2) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.colorize("&eInvalid index."));
                return true;
            }
        }
        if (!plugin.announcer().broadcastNow(index)) {
            sender.sendMessage(plugin.msg("no-lines"));
            return true;
        }
        sender.sendMessage(plugin.msg("broadcast-sent"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "list", "now").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
