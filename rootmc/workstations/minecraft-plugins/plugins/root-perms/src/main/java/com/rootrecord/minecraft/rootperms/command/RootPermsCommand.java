package com.rootrecord.minecraft.rootperms.command;

import com.rootrecord.minecraft.rootperms.RootPermsPlugin;
import com.rootrecord.minecraft.rootperms.service.PermsServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class RootPermsCommand implements CommandExecutor, TabCompleter {

    private final RootPermsPlugin plugin;

    public RootPermsCommand(RootPermsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootperms.admin")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }
        PermsServiceImpl perms = plugin.perms();
        if (perms == null) {
            sender.sendMessage(color("&cRoot-Perms not ready (MySQL?)."));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage(color("&6--- Root-Perms ---"));
                sender.sendMessage(color("&7server-id: &f" + perms.config().serverId()));
                sender.sendMessage(color("&7default-group: &f" + perms.config().defaultGroup()));
                sender.sendMessage(color("&7mysql: &f" + (perms.config().mysqlReady() ? "ok" : "missing")));
                sender.sendMessage(color(
                        "&7Vault Permission: &f"
                                + (Bukkit.getPluginManager().getPlugin("Vault") != null ? "available" : "absent")));
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(color("&aRoot-Perms reloaded."));
            }
            case "user" -> {
                if (args.length < 3) {
                    sender.sendMessage(color("&e/rootperms user <player> <info|addgroup|removegroup> [group]"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(color("&cPlayer must be online."));
                    return true;
                }
                String action = args[2].toLowerCase(Locale.ROOT);
                if ("info".equals(action)) {
                    sender.sendMessage(color("&7Groups: &f" + String.join(", ", perms.groupsOf(target.getUniqueId()))));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(color("&eNeed group id."));
                    return true;
                }
                String group = args[3];
                boolean ok = "addgroup".equals(action)
                        ? perms.grantGroup(target.getUniqueId(), group)
                        : perms.revokeGroup(target.getUniqueId(), group);
                sender.sendMessage(color(ok ? "&aDone." : "&cFailed."));
            }
            case "group" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&e/rootperms group <id>"));
                    return true;
                }
                sender.sendMessage(color(
                        "&7"
                                + args[1]
                                + " display=&f"
                                + perms.groupDisplay(args[1])
                                + " &7prefix=&f"
                                + perms.groupPrefix(args[1])));
            }
            default -> sender.sendMessage(color("&e/rootperms <status|user|group|reload>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rootperms.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("status", "user", "group", "reload"), args[0]);
        }
        if (args.length == 2 && "user".equalsIgnoreCase(args[0])) {
            return null;
        }
        if (args.length == 3 && "user".equalsIgnoreCase(args[0])) {
            return filter(List.of("info", "addgroup", "removegroup"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).collect(Collectors.toCollection(ArrayList::new));
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
