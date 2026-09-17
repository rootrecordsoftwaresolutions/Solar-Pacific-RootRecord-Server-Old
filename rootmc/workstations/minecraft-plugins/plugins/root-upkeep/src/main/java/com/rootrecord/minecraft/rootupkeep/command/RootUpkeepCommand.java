package com.rootrecord.minecraft.rootupkeep.command;

import com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin;
import com.rootrecord.minecraft.rootupkeep.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class RootUpkeepCommand implements CommandExecutor, TabCompleter {

    private final RootUpkeepPlugin plugin;

    public RootUpkeepCommand(RootUpkeepPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootupkeep.admin")) {
            Messages.send(sender, plugin.config().noPermissionMessage());
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " reload|run");
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            plugin.reloadAll();
            Messages.send(sender, plugin.config().reloadDoneMessage());
            return true;
        }
        if ("run".equalsIgnoreCase(args[0])) {
            plugin.scheduler().runNow(true);
            sender.sendMessage("Inactivity tax run queued.");
            return true;
        }
        sender.sendMessage("Usage: /" + label + " reload|run");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rootupkeep.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("reload", "run").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
