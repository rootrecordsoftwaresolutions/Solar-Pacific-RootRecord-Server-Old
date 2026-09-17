package com.rootrecord.minecraft.rootskills.command;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class TalentsCommand implements CommandExecutor, TabCompleter {

    private final RootSkillsPlugin plugin;

    public TalentsCommand(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("general.players-only"));
            return true;
        }
        if (!plugin.getConfig().getBoolean("features.talents", true)) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        plugin.talentLoadoutGui().open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
