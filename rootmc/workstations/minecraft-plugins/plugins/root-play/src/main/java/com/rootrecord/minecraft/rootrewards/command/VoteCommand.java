package com.rootrecord.minecraft.rootrewards.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.common.ListingSiteCanonical;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootrewards.config.RewardsConfig.VoteLink;
import com.rootrecord.minecraft.rootrewards.data.VoteTotals;
import com.rootrecord.minecraft.rootrewards.service.VotePerkService;
import com.rootrecord.minecraft.rootrewards.service.VoteSiteCooldown;
import com.rootrecord.minecraft.rootstat.RootStatBridge;
import com.rootrecord.minecraft.rootstat.cloud.CloudApiClient;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class VoteCommand implements CommandExecutor {

    private final RootRewardsPlugin plugin;

    public VoteCommand(RootRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var cfg = plugin.rewardsConfig();
        ChatUi.banner(sender, "Vote");
        sendRewardNote(sender, cfg.voteGoldMin(), cfg.voteGoldMax(), cfg.voteAppreciationTokens());
        ChatUi.entry(
                sender,
                "Perk",
                VotePerkService.REQUIRED
                        + " votes / 24h → ½ tax + ½ death loss");

        if (cfg.voteLinks().isEmpty()) {
            ChatUi.tip(sender, "Ask staff to add vote links in root-rewards.yml.");
            if (!plugin.listenerOnline()) {
                ChatUi.entry(sender, "Listener", "offline · try later or /discord", "alert");
            } else if (!plugin.votifierActive() && plugin.rewardsConfig().voteNetworkListener()) {
                ChatUi.entry(sender, "Listener", "online · Towny ingest", "ok");
            }
            if (sender instanceof Player player) {
                appendGovernanceSection(player);
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            for (var link : cfg.voteLinks()) {
                ChatUi.entry(sender, link.name(), link.url());
            }
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            Map<String, Instant> lastByService = Map.of();
            VoteTotals totals = VoteTotals.empty();
            VotePerkService.Snapshot perk = VotePerkService.Snapshot.none();
            try {
                lastByService = plugin.store().lastVotesByService(player.getUniqueId());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load vote timers for " + player.getName(), ex);
            }
            try {
                totals = plugin.store().readVoteTotals(player.getUniqueId());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load vote totals for " + player.getName(), ex);
            }
            try {
                if (plugin.votePerk() != null) {
                    perk = plugin.votePerk().snapshot(player.getUniqueId());
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load vote perk for " + player.getName(), ex);
            }
            Map<String, Instant> byCanonical = indexByCanonical(lastByService);
            Instant now = Instant.now();
            VoteTotals totalsFinal = totals;
            VotePerkService.Snapshot perkFinal = perk;
            int shards = digitalVoteShards(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin.host(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                sendPlayerVoteNote(player, totalsFinal, shards);
                sendPerkNote(player, perkFinal);
                for (VoteLink link : plugin.rewardsConfig().voteLinks()) {
                    Instant last = lookupLastVote(byCanonical, link);
                    boolean available = VoteSiteCooldown.available(link, last, now);
                    String remaining = null;
                    if (!available) {
                        Duration wait = Duration.between(now, VoteSiteCooldown.nextEligibleAt(link, last));
                        remaining = VoteSiteCooldown.formatRemaining(wait);
                    }
                    player.sendMessage(ChatLinks.voteSiteLine(link.name(), link.url(), available, remaining));
                }
                if (!plugin.listenerOnline()) {
                    ChatUi.entry(player, "Listener", "offline · try later or /discord", "alert");
                } else if (!plugin.votifierActive() && plugin.rewardsConfig().voteNetworkListener()) {
                    ChatUi.entry(player, "Listener", "online · Towny ingest", "ok");
                }
                ChatUi.links(
                        player,
                        "Thanks",
                        "https://rootmc.net/thanks/",
                        "Buy Voteshards",
                        "https://rootmc.net/pro/vote-shards/");
                appendGovernanceSection(player);
            });
        });
        return true;
    }

    private void sendRewardNote(CommandSender sender, int goldMin, int goldMax, int tokensPerVote) {
        int tokens = Math.max(0, tokensPerVote);
        String tokenBit = tokens <= 0 ? "" : " · +" + tokens + " token";
        ChatUi.entry(
                sender,
                "Reward",
                "+" + goldMin + "–" + goldMax + " G / site" + tokenBit + " · +1 shard");
    }

    private static void sendPerkNote(Player player, VotePerkService.Snapshot perk) {
        if (perk == null) {
            return;
        }
        if (perk.active()) {
            ChatUi.entry(
                    player,
                    "Boost",
                    "½ tax + ½ death · " + perk.remainingLabel() + " left · "
                            + perk.votes() + "/" + perk.required(),
                    "ok");
            return;
        }
        int need = Math.max(0, perk.required() - perk.votes());
        ChatUi.entry(
                player,
                "Boost",
                perk.votes() + "/" + perk.required() + " this window · " + need + " more",
                "open");
    }

    private void sendPlayerVoteNote(Player player, VoteTotals totals, int shards) {
        int votes = Math.max(0, totals.voteCount());
        int inv = appreciationTokensInInventory(player);
        String tokenBit = inv >= 0 ? inv + " tokens on you" : "tokens via /thanks";
        String shardBit = shards >= 0 ? shards + " shards" : "shards via /voteshard";
        ChatUi.entry(player, "You", votes + " votes · " + shardBit + " · " + tokenBit);
    }

    /** Issued Vote Shard ledger (listing + Stripe + membership), not Gold. */
    private static int digitalVoteShards(UUID uuid) {
        if (uuid == null) {
            return -1;
        }
        try {
            Plugin app = Bukkit.getPluginManager().getPlugin("Root-Appreciation");
            if (app == null || !app.isEnabled()) {
                return -1;
            }
            Object svc = app.getClass().getMethod("voteShardService").invoke(app);
            Object n = svc.getClass().getMethod("digitalIssued", UUID.class).invoke(svc, uuid);
            return n instanceof Number ? Math.max(0, ((Number) n).intValue()) : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private static int appreciationTokensInInventory(Player player) {
        try {
            Plugin app = Bukkit.getPluginManager().getPlugin("Root-Appreciation");
            if (app == null || !app.isEnabled()) {
                return -1;
            }
            Object tokens = app.getClass().getMethod("tokens").invoke(app);
            Object n = tokens.getClass()
                    .getMethod("countInInventory", org.bukkit.entity.Player.class)
                    .invoke(tokens, player);
            return n instanceof Number ? ((Number) n).intValue() : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private static Map<String, Instant> indexByCanonical(Map<String, Instant> lastByService) {
        Map<String, Instant> out = new HashMap<>();
        for (var entry : lastByService.entrySet()) {
            String key = ListingSiteCanonical.canonicalize(entry.getKey());
            Instant existing = out.get(key);
            if (existing == null || entry.getValue().isAfter(existing)) {
                out.put(key, entry.getValue());
            }
        }
        return out;
    }

    private static Instant lookupLastVote(Map<String, Instant> byCanonical, VoteLink link) {
        Instant byName = byCanonical.get(ListingSiteCanonical.canonicalize(link.name()));
        if (byName != null) {
            return byName;
        }
        Instant byUrl = byCanonical.get(ListingSiteCanonical.canonicalize(link.url()));
        if (byUrl != null) {
            return byUrl;
        }
        String want = ListingSiteCanonical.canonicalize(link.name());
        Instant best = null;
        for (var e : byCanonical.entrySet()) {
            if (!want.equals(e.getKey()) && !want.equals(ListingSiteCanonical.canonicalize(e.getKey()))) {
                continue;
            }
            if (best == null || e.getValue().isAfter(best)) {
                best = e.getValue();
            }
        }
        return best;
    }

    private void appendGovernanceSection(Player player) {
        Plugin rootmc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (!(rootmc instanceof RootStatBridge bridge) || !rootmc.isEnabled()) {
            ChatUi.tip(player, "Council share + Pro/Lifetime multipliers: /link then https://rootmc.net/council/");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            try {
                CloudApiClient.GovernanceVotingPower power =
                        new com.rootrecord.minecraft.rootstat.governance.LocalGovernancePowerService(bridge)
                                .resolve(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin.host(), () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!power.ok()) {
                        ChatUi.entry(player, "Power", "link Discord · /link", "open");
                        ChatUi.links(player, "Verify", "https://rootmc.net/verify");
                        return;
                    }
                    if (!power.eligible()) {
                        String reason = power.summary() != null ? power.summary().trim().toLowerCase() : "";
                        if (reason.contains("link")) {
                            ChatUi.entry(player, "Power", "link Discord · /link", "open");
                            ChatUi.links(player, "Verify", "https://rootmc.net/verify");
                            if (plugin.proposalNotify() != null) {
                                plugin.proposalNotify().appendToVoteCommand(player);
                            }
                            return;
                        }
                        ChatUi.entry(player, "Power", "vote a listing site to earn Council weight", "open");
                        if (plugin.proposalNotify() != null) {
                            plugin.proposalNotify().appendToVoteCommand(player);
                        }
                        return;
                    }
                    String pct = String.format("%.2f", power.sharePercent());
                    ChatUi.entry(player, "Power", pct + "%");
                    if (plugin.proposalNotify() != null) {
                        plugin.proposalNotify().appendToVoteCommand(player);
                    }
                });
            } catch (Exception ignored) {
                if (plugin.proposalNotify() != null) {
                    plugin.proposalNotify().appendToVoteCommand(player);
                }
            }
        });
    }

}
