package com.rootrecord.minecraft.rootping;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class PingCommand implements CommandExecutor, TabCompleter {

    private final RootPingPlugin plugin;

    public PingCommand(RootPingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("rootping.admin")) {
                sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(plugin.colorize("&aRoot-Ping reloaded."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rootping.use")) {
            player.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (!plugin.config().enabled()) {
            player.sendMessage(plugin.colorize(plugin.config().disabled()));
            return true;
        }
        try {
            ConnectionSample sample = plugin.service().sampleAndStore(player, ConnectionSample.TRIGGER_COMMAND);
            if (sample == null) {
                player.sendMessage(plugin.colorize("&7Ping: &f" + player.getPing() + "ms"));
                return true;
            }
            plugin.service().sendRichPing(player, sample);
        } catch (Throwable t) {
            plugin.getLogger().warning("/ping failed for " + player.getName() + ": " + t.getMessage());
            player.sendMessage(plugin.colorize("&7Ping: &f" + player.getPing() + "ms"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("rootping.admin")) {
            String partial = args[0].toLowerCase();
            if ("reload".startsWith(partial)) {
                return List.of("reload");
            }
        }
        return Collections.emptyList();
    }
}
