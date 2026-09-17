package com.rootrecord.minecraft.roothelp.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class RulesCommand implements CommandExecutor {

    private final RootHelpPlugin plugin;

    public RulesCommand(RootHelpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roothelp.rules")) {
            sender.sendMessage(plugin.colorize("&cYou don't have permission."));
            return true;
        }
        ChatUi.banner(sender, "RootMC Rules");
        List<String> rules = plugin.helpConfig().rules();
        if (rules == null || rules.isEmpty()) {
            ChatUi.entry(sender, "Rules", "unset · tell staff", "alert");
        } else {
            for (String rule : rules) {
                if (rule == null || rule.isBlank()) {
                    continue;
                }
                sender.sendMessage(plugin.colorize("&8• " + rule));
            }
        }
        ChatUi.links(sender, "Constitution", "https://rootmc.net/wiki/constitution/");
        ChatUi.tip(sender, "/cmds  ·  /discord  ·  /map");
        return true;
    }
}
