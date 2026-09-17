package com.rootrecord.minecraft.roothelp.command;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcServerDisplay;
import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FeedbackCommand implements CommandExecutor {

    private static final int MAX_LEN = 500;
    private static final double REWARD_G = 5.0;
    private static final long REWARD_COOLDOWN_MS = 5L * 60_000L;

    private final RootHelpPlugin plugin;
    private final Map<UUID, Long> lastPaidMs = new ConcurrentHashMap<>();

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
        String message = String.join(" ", args).trim();
        if (message.length() > MAX_LEN) {
            sender.sendMessage(plugin.msg("feedback-too-long").replace("{max}", String.valueOf(MAX_LEN)));
            return true;
        }
        if (!plugin.cloud().hasCredentials()) {
            sender.sendMessage(plugin.msg("feedback-no-cloud"));
            return true;
        }

        boolean paid = tryPayReward(player);
        String serverName = RootMcServerDisplay.serverName(plugin.host());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            try {
                plugin.cloud().submitFeedback(
                        player.getUniqueId().toString(),
                        player.getName(),
                        serverName,
                        message);
                plugin.getServer().getScheduler().runTask(plugin.host(), () ->
                        player.sendMessage(plugin.msg(paid ? "feedback-sent-paid" : "feedback-sent-recent")));
            } catch (Exception ex) {
                plugin.getServer().getScheduler().runTask(plugin.host(), () ->
                        player.sendMessage(plugin.colorize(
                                plugin.rawMsg("feedback-fail").replace("{error}", ex.getMessage()))));
            }
        });
        return true;
    }

    private boolean tryPayReward(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastPaidMs.get(player.getUniqueId());
        if (last != null && now - last < REWARD_COOLDOWN_MS) {
            return false;
        }
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin.host());
        if (eco == null) {
            return false;
        }
        try {
            eco.deposit(player.getUniqueId(), REWARD_G);
            lastPaidMs.put(player.getUniqueId(), now);
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Feedback reward deposit failed: " + ex.getMessage());
            return false;
        }
    }
}
