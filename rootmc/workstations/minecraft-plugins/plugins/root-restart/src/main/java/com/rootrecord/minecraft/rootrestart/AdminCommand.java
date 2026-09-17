package com.rootrecord.minecraft.rootrestart;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final RootRestartPlugin plugin;

    public AdminCommand(RootRestartPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean stopMode = isStopCommand(command, label);
        String usage = stopMode ? "/rootstop [cancel]" : "/rootrestart [morning|cancel]";

        if (args.length >= 1 && "cancel".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("rootrestart.cancel")) {
                sender.sendMessage(plugin.colorize("&cNo permission."));
                return true;
            }
            if (plugin.cancelCountdown()) {
                RestartConfig cfg = plugin.config();
                String line = plugin.colorize(cfg.prefix() + cfg.cancelledMsg());
                org.bukkit.Bukkit.broadcastMessage(line);
                sender.sendMessage(line);
                RestartDiscordRelay.relay(plugin, line);
            } else {
                sender.sendMessage(plugin.colorize("&7No countdown is running."));
            }
            return true;
        }
        boolean morning = args.length >= 1 && "morning".equalsIgnoreCase(args[0]);
        if (args.length >= 1 && !morning) {
            sender.sendMessage(plugin.colorize("&eUsage: " + usage));
            return true;
        }
        if (!sender.hasPermission("rootrestart.admin")) {
            sender.sendMessage(plugin.colorize("&cNo permission."));
            return true;
        }
        if (plugin.countdown() != null && plugin.countdown().isRunning()) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + plugin.config().alreadyRunningMsg()));
            return true;
        }
        String name = sender instanceof Player p ? p.getName() : "Console";
        RestartCountdown.Kind kind = stopMode
                ? RestartCountdown.Kind.STOP
                : morning ? RestartCountdown.Kind.MORNING : RestartCountdown.Kind.MANUAL;
        plugin.startCountdown(kind, name);
        RestartConfig cfg = plugin.config();
        String startedBody = stopMode
                ? cfg.startedStopMsg().replace("{player}", name)
                : cfg.startedManualMsg().replace("{player}", name);
        String prefix = stopMode ? cfg.stopPrefix() : cfg.prefix();
        String started = plugin.colorize(prefix + startedBody);
        org.bukkit.Bukkit.broadcastMessage(started);
        sender.sendMessage(started);
        RestartDiscordRelay.relay(plugin, started);
        return true;
    }

    private static boolean isStopCommand(Command command, String label) {
        String name = command != null && command.getName() != null
                ? command.getName()
                : (label == null ? "" : label);
        String key = name.toLowerCase(Locale.ROOT);
        return key.equals("rootstop") || key.equals("rrstop");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission("rootrestart.cancel") && "cancel".startsWith(pfx)) {
                out.add("cancel");
            }
            if (!isStopCommand(command, alias)
                    && sender.hasPermission("rootrestart.admin")
                    && "morning".startsWith(pfx)) {
                out.add("morning");
            }
        }
        return out;
    }
}
