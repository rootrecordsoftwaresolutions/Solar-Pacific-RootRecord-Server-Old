package com.rootrecord.minecraft.rootskills.command;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class ClassCommand implements CommandExecutor, TabCompleter {

    private final RootSkillsPlugin plugin;

    public ClassCommand(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("general.players-only"));
            return true;
        }
        if (!plugin.classManager().enabled()) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (args.length == 0) {
            String classId = plugin.repository().getOrCreate(player.getUniqueId()).classId();
            var def = classId == null ? null : plugin.classManager().get(classId).orElse(null);
            player.sendMessage(plugin.msg("classes.info")
                    .replace("{class}", classId == null ? "none" : classId)
                    .replace("{description}", def == null ? "" : def.description()));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("select".equals(sub) && args.length >= 2) {
            if (plugin.classManager().select(player.getUniqueId(), args[1])) {
                player.sendMessage(plugin.msg("classes.selected").replace("{class}", args[1]));
            } else {
                player.sendMessage(plugin.msg("classes.unknown").replace("{class}", args[1]));
            }
            return true;
        }
        var def = plugin.classManager().get(args[0]).orElse(null);
        if (def == null) {
            player.sendMessage(plugin.msg("classes.unknown").replace("{class}", args[0]));
            return true;
        }
        player.sendMessage(plugin.msg("classes.info")
                .replace("{class}", def.displayName())
                .replace("{description}", def.description()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new java.util.ArrayList<>(List.of("info", "select"));
            base.addAll(plugin.classManager().all().keySet());
            return base.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && "select".equalsIgnoreCase(args[0])) {
            return plugin.classManager().all().keySet().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
