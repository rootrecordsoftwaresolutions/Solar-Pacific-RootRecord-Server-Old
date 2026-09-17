package com.rootrecord.minecraft.rootannouncer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AnnouncerToggleCommand implements CommandExecutor, TabCompleter {

    private final RootAnnouncerPlugin plugin;

    public AnnouncerToggleCommand(RootAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootannouncer.toggle")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (args.length == 0) {
            boolean enabled = plugin.toggles().toggle(uuid);
            player.sendMessage(plugin.msg(enabled ? "toggle-on" : "toggle-off"));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on", "enable" -> {
                plugin.toggles().setEnabled(uuid, true);
                player.sendMessage(plugin.msg("toggle-on"));
                yield true;
            }
            case "off", "disable" -> {
                plugin.toggles().setEnabled(uuid, false);
                player.sendMessage(plugin.msg("toggle-off"));
                yield true;
            }
            case "status" -> {
                player.sendMessage(plugin.msg(
                        plugin.toggles().isEnabled(uuid) ? "toggle-status-on" : "toggle-status-off"));
                yield true;
            }
            default -> {
                player.sendMessage(plugin.colorize("&eUsage: /" + label + " [on|off|status]"));
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("on", "off", "status").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
