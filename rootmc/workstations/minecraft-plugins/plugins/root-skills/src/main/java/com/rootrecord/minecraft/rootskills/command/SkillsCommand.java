package com.rootrecord.minecraft.rootskills.command;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SkillsCommand implements CommandExecutor, TabCompleter {

    private final RootSkillsPlugin plugin;

    public SkillsCommand(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("general.players-only"));
            return true;
        }
        if (args.length == 0) {
            plugin.skillsHubGui().open(player);
            return true;
        }
        Optional<SkillId> skill = SkillId.fromString(args[0]);
        if (skill.isEmpty()) {
            player.sendMessage(plugin.msg("skills.unknown").replace("{skill}", args[0]));
            return true;
        }
        var progress = plugin.repository().getOrCreate(player.getUniqueId()).skill(skill.get());
        long next = plugin.xpService().formula().xpToNext(progress.level());
        player.sendMessage(plugin.msg("skills.progress")
                .replace("{skill}", skill.get().key())
                .replace("{level}", Integer.toString(progress.level()))
                .replace("{xp}", Long.toString(progress.xp()))
                .replace("{next}", Long.toString(next)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (SkillId id : SkillId.values()) {
            if (id.key().startsWith(prefix)) {
                out.add(id.key());
            }
        }
        return out;
    }
}
