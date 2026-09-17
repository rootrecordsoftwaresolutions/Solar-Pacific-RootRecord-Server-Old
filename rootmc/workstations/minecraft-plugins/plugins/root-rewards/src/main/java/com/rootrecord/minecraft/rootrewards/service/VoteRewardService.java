package com.rootrecord.minecraft.rootrewards.service;

import com.rootrecord.minecraft.common.ListingSiteCanonical;
import com.rootrecord.minecraft.common.RootMcPublicReachout;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootrewards.data.RewardsStore;
import com.rootrecord.minecraft.rootrewards.data.VoteTotals;
import com.rootrecord.minecraft.rootrewards.economy.RewardsEconomy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.logging.Level;

public final class VoteRewardService {

    private final RootRewardsPlugin plugin;
    private final RewardsStore store;
    private final RewardsEconomy economy;

    public VoteRewardService(RootRewardsPlugin plugin, RewardsStore store, RewardsEconomy economy) {
        this.plugin = plugin;
        this.store = store;
        this.economy = economy;
    }

    /** Grant gold when Votifier receives a vote — listing sites enforce their own cooldowns. */
    public void handleVote(String username, String service) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                OfflinePlayer lookedUp = Bukkit.getOfflinePlayerIfCached(username);
                if (lookedUp == null || !lookedUp.hasPlayedBefore()) {
                    lookedUp = Bukkit.getOfflinePlayer(username);
                }
                final OfflinePlayer player = lookedUp;
                if (!player.hasPlayedBefore()) {
                    plugin.getLogger().info("Vote from unknown player: " + username);
                    return;
                }
                if (player.isOnline()) {
                    var online = player.getPlayer();
                    if (online != null
                            && !online.hasPermission("rootrewards.vote")
                            && !online.hasPermission("rootrewards.use")) {
                        return;
                    }
                }

                String svc = ListingSiteCanonical.canonicalize(service);
                if (!service.equalsIgnoreCase(svc)) {
                    plugin.getLogger().info("Vote service normalized: " + service + " -> " + svc);
                }
                double gold = plugin.rewardsConfig().rollVoteGold();
                RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
                RootMcIncomeSweepResult sweep;
                if (treasury != null) {
                    sweep = treasury.payVoteReward(player.getUniqueId(), player.getName(), gold, svc);
                    if (sweep == null) {
                        plugin.getLogger().warning(
                                "Vote reward treasury payout failed for " + username + " (" + svc + ")");
                        return;
                    }
                } else {
                    plugin.getLogger().warning(
                            "Vote reward skipped — treasury unavailable for " + username + " (" + svc + ")");
                    return;
                }

                VoteTotals totals = store.recordVote(player.getUniqueId(), svc, gold);
                RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
                if (reachout != null && sweep.toWallet() > 0) {
                    reachout.recordTreasuryOutflow(
                            "vote",
                            player.getName(),
                            player.getUniqueId(),
                            sweep.toWallet(),
                            false);
                }
                relayVoteToDiscord(username, svc, sweep.toWallet(), totals);
                maybeBroadcastMilestone(username, totals);

                if (player.isOnline() && sweep.toWallet() > 0) {
                    String msg = plugin.rawMsg("vote-grant")
                            .replace("{service}", svc)
                            .replace("{gold}", formatGold(sweep.toWallet()));
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.getPlayer().sendMessage(plugin.msg(msg));
                        }
                    });
                }
                plugin.getLogger().info("Vote reward +" + gold + " G to " + username + " (" + svc + ")");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Vote reward failed for " + username, ex);
            }
        });
    }

    private void relayVoteToDiscord(String username, String service, double earnedGold, VoteTotals totals) {
        if (!plugin.rewardsConfig().voteDiscordRelay() || earnedGold <= 0) {
            return;
        }
        String template = plugin.rawMsg("vote-discord-broadcast");
        if (template == null || template.isBlank()) {
            return;
        }
        String line = template
                .replace("{player}", username)
                .replace("{service}", service)
                .replace("{gold}", formatGold(earnedGold))
                .replace("{total_gold}", formatGold(totals.totalGold()))
                .replace("{total_votes}", String.valueOf(totals.voteCount()));
        RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
        if (reachout != null) {
            reachout.relayGlobalBroadcast(plugin.colorize(line), "vote");
        }
    }

    private void maybeBroadcastMilestone(String username, VoteTotals totals) {
        if (!plugin.rewardsConfig().voteBroadcastEnabled()) {
            return;
        }
        int every = plugin.rewardsConfig().voteBroadcastEvery();
        if (every < 1 || totals.voteCount() < 1 || totals.voteCount() % every != 0) {
            return;
        }
        String template = plugin.rawMsg("vote-milestone-broadcast");
        if (template == null || template.isBlank()) {
            return;
        }
        String line = template
                .replace("{player}", username)
                .replace("{total_gold}", formatGold(totals.totalGold()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(plugin.colorize(line));
            RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
            if (reachout != null) {
                reachout.relayGlobalBroadcast(line, "vote_milestone");
            }
        });
    }

    private static String formatGold(double gold) {
        if (gold == Math.rint(gold)) {
            return String.valueOf((long) gold);
        }
        return String.valueOf(gold);
    }
}
