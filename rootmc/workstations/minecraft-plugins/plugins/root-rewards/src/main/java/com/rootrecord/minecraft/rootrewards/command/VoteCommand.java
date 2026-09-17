package com.rootrecord.minecraft.rootrewards.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.ListingSiteCanonical;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootrewards.config.RewardsConfig.VoteLink;
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
import java.util.logging.Level;

public final class VoteCommand implements CommandExecutor {

    private final RootRewardsPlugin plugin;

    public VoteCommand(RootRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var cfg = plugin.rewardsConfig();
        sender.sendMessage(plugin.msg(plugin.rawMsg("vote-links-header")
                .replace("{gold_min}", String.valueOf(cfg.voteGoldMin()))
                .replace("{gold_max}", String.valueOf(cfg.voteGoldMax()))));
        if (cfg.voteLinks().isEmpty()) {
            sender.sendMessage(plugin.colorize("&7Ask staff to add vote links in root-rewards.yml."));
            if (!plugin.votifierActive()) {
                sender.sendMessage(plugin.msg("vote-no-votifier"));
            }
            if (sender instanceof Player player) {
                appendGovernanceSection(player);
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            for (var link : cfg.voteLinks()) {
                sender.sendMessage(plugin.colorize("&8  &b" + link.name() + "&7: &f" + link.url()));
            }
            if (!plugin.votifierActive()) {
                sender.sendMessage(plugin.msg("vote-no-votifier"));
            }
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Instant> lastByService = Map.of();
            try {
                lastByService = plugin.store().lastVotesByService(player.getUniqueId());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load vote timers for " + player.getName(), ex);
            }
            Map<String, Instant> byCanonical = indexByCanonical(lastByService);
            Instant now = Instant.now();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
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
                if (!plugin.votifierActive()) {
                    player.sendMessage(plugin.msg("vote-no-votifier"));
                }
                appendGovernanceSection(player);
            });
        });
        return true;
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
        return byCanonical.get(ListingSiteCanonical.canonicalize(link.url()));
    }

    private void appendGovernanceSection(Player player) {
        Plugin rootmc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (!(rootmc instanceof RootStatBridge bridge) || !rootmc.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CloudApiClient.GovernanceVotingPower power =
                        bridge.cloud().fetchGovernanceVotingPower(player.getUniqueId().toString());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!power.ok()) {
                        player.sendMessage(plugin.msg("vote-governance-not-linked"));
                        return;
                    }
                    if (!power.eligible()) {
                        player.sendMessage(plugin.msg("vote-governance-not-eligible"));
                        return;
                    }
                    String pct = String.format("%.3f", power.sharePercent());
                    player.sendMessage(plugin.msg(plugin.rawMsg("vote-governance-header")));
                    player.sendMessage(plugin.msg(plugin.rawMsg("vote-governance-share").replace("{percent}", pct)));
                    sendGovernanceSummary(player, power.summary());
                    if (power.votingChannelUrl() != null && !power.votingChannelUrl().isBlank()) {
                        player.sendMessage(ChatLinks.labelButtonUrl("Official polls (Discord)", power.votingChannelUrl()));
                    }
                    if (power.constitutionUrl() != null && !power.constitutionUrl().isBlank()) {
                        player.sendMessage(ChatLinks.labelButtonUrl("Constitution", power.constitutionUrl()));
                    }
                });
            } catch (Exception ignored) {
                // Cloud unreachable  -  listing vote links still shown
            }
        });
    }

    private void sendGovernanceSummary(Player player, String summary) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        for (String line : summary.split("\\n")) {
            String cleaned = line.replace("**", "").trim();
            if (cleaned.isEmpty() || cleaned.toLowerCase().contains("of total governance power")) {
                continue;
            }
            player.sendMessage(plugin.colorize("&7" + cleaned.replace('\u00a7', '&')));
        }
    }
}
