package com.rootrecord.minecraft.roothelp.command;

import com.rootrecord.minecraft.common.RootMcServerDisplay;
import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FeedbackCommand implements CommandExecutor {

    private static final int MAX_LEN = 500;

    private final RootHelpPlugin plugin;

    public FeedbackCommand(RootHelpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roothelp.feedback")) {
            sender.sendMessage(plugin.colorize("&cYou don't have permission."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cPlayers only."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.msg("feedback-usage"));
            return true;
        }
        String landErr = HelpLandAccess.requireOwnLand(player);
        if (landErr != null) {
            sender.sendMessage(plugin.msg(landErr));
            return true;
        }
        String message = String.join(" ", args).trim();
        if (message.length() > MAX_LEN) {
            sender.sendMessage(plugin.msg("feedback-too-long").replace("{max}", String.valueOf(MAX_LEN)));
            return true;
        }
        if (!plugin.cloud().hasCredentials()) {
            sender.sendMessage(plugin.msg("feedback-no-cloud"));
            return true;
        }

        String serverName = RootMcServerDisplay.serverName(plugin);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.cloud().submitFeedback(
                        player.getUniqueId().toString(),
                        player.getName(),
                        serverName,
                        message);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.msg("feedback-sent")));
            } catch (Exception ex) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.colorize(
                                plugin.rawMsg("feedback-fail").replace("{error}", ex.getMessage()))));
            }
        });
        return true;
    }
}
