package com.rootrecord.minecraft.rootrewards.service;

import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootrewards.data.RewardsStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.logging.Level;

public final class VoteReminderService {

    private final RootRewardsPlugin plugin;
    private final RewardsStore store;

    public VoteReminderService(RootRewardsPlugin plugin, RewardsStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Remind online players who have not voted within the configured window. */
    public void remindOnlinePlayers() {
        var config = plugin.rewardsConfig();
        if (!config.voteReminderEnabled() || !config.mysqlEnabled()) {
            return;
        }
        Player[] online = Bukkit.getOnlinePlayers().toArray(Player[]::new);
        if (online.length == 0) {
            return;
        }
        long staleMs = config.voteReminderAfterHours() * 3_600_000L;
        Instant cutoff = Instant.now().minusMillis(staleMs);
        String template = plugin.rawMsg("vote-reminder");
        if (template == null || template.isBlank()) {
            return;
        }
        String messageBody = template
                .replace("{gold_min}", String.valueOf(config.voteGoldMin()))
                .replace("{gold_max}", String.valueOf(config.voteGoldMax()));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player player : online) {
                if (!player.hasPermission("rootrewards.vote") && !player.hasPermission("rootrewards.use")) {
                    continue;
                }
                try {
                    var lastVote = store.lastVoteAtAny(player.getUniqueId());
                    if (lastVote.isPresent() && !lastVote.get().isBefore(cutoff)) {
                        continue;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage(plugin.msg(messageBody));
                        }
                    });
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Vote reminder failed for " + player.getName(), ex);
                }
            }
        });
    }
}
