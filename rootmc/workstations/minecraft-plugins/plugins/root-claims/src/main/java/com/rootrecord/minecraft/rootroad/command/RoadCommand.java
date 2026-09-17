package com.rootrecord.minecraft.rootroad.command;

import com.rootrecord.minecraft.rootroad.RoadGuideService;
import com.rootrecord.minecraft.rootroad.RoadStep;
import com.rootrecord.minecraft.rootroad.RootRoadPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class RoadCommand implements CommandExecutor, TabCompleter {

    private final RootRoadPlugin plugin;
    private final RoadGuideService guide;

    public RoadCommand(RootRoadPlugin plugin, RoadGuideService guide) {
        this.plugin = plugin;
        this.guide = guide;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            RoadStep step = guide.status(player.getUniqueId());
            if (step.isComplete()) {
                player.sendMessage(plugin.msg("status-complete"));
            } else {
                player.sendMessage(plugin.msg("status-active").replace("{step}", step.name()));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rootroad.reload")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }
        sender.sendMessage(plugin.colorize("&eUsage: /road [status|reload]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("status", "reload")
                    .filter(s -> s.startsWith(lower))
                    .toList();
        }
        return List.of();
    }
}
