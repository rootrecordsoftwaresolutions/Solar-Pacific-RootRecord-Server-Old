package com.rootrecord.minecraft.rootrewards.service;

import com.rootrecord.minecraft.common.ListingSiteCanonical;
import com.rootrecord.minecraft.common.RootMcPublicReachout;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootrewards.data.RewardsStore;
import com.rootrecord.minecraft.rootrewards.data.VoteInsert;
import com.rootrecord.minecraft.rootrewards.data.VoteTotals;
import com.rootrecord.minecraft.rootrewards.economy.RewardsEconomy;
import com.rootrecord.minecraft.rootstat.RootStatBridge;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

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
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
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
                RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin.host());
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

                VotePerkService.Snapshot perkBefore = snapshotPerk(player.getUniqueId());
                VoteInsert insert = store.recordVoteWithId(player.getUniqueId(), svc, gold);
                VoteTotals totals = insert.totals();
                if (plugin.votePerk() != null) {
                    plugin.votePerk().invalidate(player.getUniqueId());
                }
                VotePerkService.Snapshot perk = snapshotPerk(player.getUniqueId());
                boolean unlocked = perk != null && perk.active() && (perkBefore == null || !perkBefore.active());
                grantVoteAppreciationTokens(player, insert.voteId());
                grantVoteShards(player, insert.voteId());
                mirrorVoteToClaims(player, svc, gold);
                RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin.host());
                if (reachout != null && sweep.toWallet() > 0) {
                    reachout.recordTreasuryOutflow(
                            "vote",
                            player.getName(),
                            player.getUniqueId(),
                            sweep.toWallet(),
                            false);
                }
                relayVoteToDiscord(username, svc, sweep.toWallet(), totals, perk);
                maybeBroadcastMilestone(username, totals);
                if (unlocked) {
                    broadcastPerkUnlock(username, perk);
                }

                if (player.isOnline() && sweep.toWallet() > 0) {
                    String svcLabel = svc;
                    String goldLabel = formatGold(sweep.toWallet());
                    String perkLabel = perk == null ? "" : perk.announceLabel();
                    Bukkit.getScheduler().runTask(plugin.host(), () -> {
                        if (player.isOnline()) {
                            com.rootrecord.minecraft.common.ChatUi.entry(
                                    player.getPlayer(),
                                    "Vote",
                                    "+" + goldLabel + " G · +1 token · +1 shard · " + svcLabel,
                                    "done");
                            if (!perkLabel.isBlank()) {
                                com.rootrecord.minecraft.common.ChatUi.entry(
                                        player.getPlayer(),
                                        "Perk",
                                        perkLabel,
                                        perk != null && perk.active() ? "ok" : "open");
                            }
                        }
                    });
                }
                plugin.getLogger().info("Vote reward +" + gold + " G to " + username + " (" + svc + ")");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Vote reward failed for " + username, ex);
            }
        });
    }

    private void grantVoteAppreciationTokens(OfflinePlayer player, long voteRowId) {
        int tokens = plugin.rewardsConfig().voteAppreciationTokens();
        if (tokens <= 0 || voteRowId <= 0 || player == null || player.getUniqueId() == null) {
            return;
        }
        try {
            Plugin rootApp = Bukkit.getPluginManager().getPlugin("Root-Appreciation");
            if (rootApp == null || !rootApp.isEnabled()) {
                return;
            }
            Object service = rootApp.getClass().getMethod("service").invoke(rootApp);
            Object ok = service.getClass()
                    .getMethod("grantVoteTokens", java.util.UUID.class, String.class, int.class, long.class)
                    .invoke(service, player.getUniqueId(), player.getName(), tokens, voteRowId);
            if (Boolean.TRUE.equals(ok)) {
                plugin.getLogger().info(
                        "Vote appreciation +" + tokens + " queued/granted for " + player.getName()
                                + " (vote #" + voteRowId + ")");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Vote appreciation grant failed for " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void grantVoteShards(OfflinePlayer player, long voteRowId) {
        if (voteRowId <= 0 || player == null || player.getUniqueId() == null) {
            return;
        }
        try {
            Plugin rootApp = Bukkit.getPluginManager().getPlugin("Root-Appreciation");
            if (rootApp == null || !rootApp.isEnabled()) {
                return;
            }
            Object vs = rootApp.getClass().getMethod("voteShardService").invoke(rootApp);
            if (vs == null) {
                return;
            }
            Object ok = vs.getClass()
                    .getMethod("grantVoteShard", java.util.UUID.class, String.class, long.class)
                    .invoke(vs, player.getUniqueId(), player.getName(), voteRowId);
            if (Boolean.TRUE.equals(ok)) {
                plugin.getLogger().info(
                        "Vote shard +1 for " + player.getName() + " (vote #" + voteRowId + ")");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Vote shard grant failed for " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void mirrorVoteToClaims(OfflinePlayer player, String service, double gold) {
        if (!plugin.rewardsConfig().voteMirrorToClaims() || gold < 0.01d) {
            return;
        }
        try {
            Plugin rootmc = Bukkit.getPluginManager().getPlugin("RootMC");
            if (!(rootmc instanceof RootStatBridge bridge) || !rootmc.isEnabled()) {
                plugin.getLogger().warning("Claims vote mirror skipped — RootMC cloud unavailable");
                return;
            }
            boolean ok = bridge.cloud().queueClaimsVoteCredit(
                    player.getUniqueId().toString(),
                    player.getName() == null ? "Player" : player.getName(),
                    service,
                    gold,
                    java.time.Instant.now().toString());
            if (ok) {
                plugin.getLogger().info(
                        "Claims vote mirror queued +" + gold + " G for " + player.getName() + " (" + service + ")");
            } else {
                plugin.getLogger().warning(
                        "Claims vote mirror not queued for " + player.getName() + " (" + service + ")");
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Claims vote mirror failed for " + player.getName(), ex);
        }
    }

    private VotePerkService.Snapshot snapshotPerk(java.util.UUID uuid) {
        if (plugin.votePerk() == null || uuid == null) {
            return VotePerkService.Snapshot.none();
        }
        try {
            return plugin.votePerk().snapshot(uuid);
        } catch (Exception ex) {
            return VotePerkService.Snapshot.none();
        }
    }

    private void relayVoteToDiscord(
            String username, String service, double earnedGold, VoteTotals totals, VotePerkService.Snapshot perk) {
        if (!plugin.rewardsConfig().voteDiscordRelay() || earnedGold <= 0) {
            return;
        }
        String template = plugin.rawMsg("vote-discord-broadcast");
        if (template == null || template.isBlank()) {
            return;
        }
        String perkBit = perk == null ? "" : perk.announceLabel();
        String line = template
                .replace("{player}", username)
                .replace("{service}", service)
                .replace("{gold}", formatGold(earnedGold))
                .replace("{total_gold}", formatGold(totals.totalGold()))
                .replace("{total_votes}", String.valueOf(totals.voteCount()))
                .replace("{perk}", perkBit);
        RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin.host());
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
                .replace("{total_gold}", formatGold(totals.totalGold()))
                .replace("{perk}", "5 votes / 24h = ½ tax + ½ death loss");
        Bukkit.getScheduler().runTask(plugin.host(), () -> {
            Bukkit.broadcastMessage(plugin.colorize(line));
            RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin.host());
            if (reachout != null) {
                reachout.relayGlobalBroadcast(line, "vote_milestone");
            }
        });
    }

    private void broadcastPerkUnlock(String username, VotePerkService.Snapshot perk) {
        String template = plugin.rawMsg("vote-perk-unlock");
        if (template == null || template.isBlank()) {
            template =
                    "&6{player} &7unlocked &f½ tax + ½ death loss &7for 24h (5 listing votes). Keep it going with &f/vote&7!";
        }
        String left = perk != null ? perk.remainingLabel() : "";
        String line = template
                .replace("{player}", username)
                .replace("{remaining}", left.isBlank() ? "24h" : left);
        Bukkit.getScheduler().runTask(plugin.host(), () -> {
            Bukkit.broadcastMessage(plugin.colorize(line));
            RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin.host());
            if (reachout != null) {
                reachout.relayGlobalBroadcast(line, "vote_perk");
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
