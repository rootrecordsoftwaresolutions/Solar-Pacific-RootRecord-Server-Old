package com.rootrecord.minecraft.rootrewards.service;

import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootrewards.PlaytimeMilestones;
import com.rootrecord.minecraft.rootrewards.PlaytimeMilestones.Milestone;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootrewards.data.RewardsStore;
import com.rootrecord.minecraft.rootrewards.economy.RewardsEconomy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class PlaytimeRewardService {

    private final RootRewardsPlugin plugin;
    private final RewardsStore store;
    private final RewardsEconomy economy;
    private final List<Milestone> milestones;

    public PlaytimeRewardService(
            RootRewardsPlugin plugin,
            RewardsStore store,
            RewardsEconomy economy,
            List<Milestone> milestones) {
        this.plugin = plugin;
        this.store = store;
        this.economy = economy;
        this.milestones = milestones;
    }

    public List<Milestone> milestones() {
        return milestones;
    }

    public void checkOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("rootrewards.use")) {
                continue;
            }
            checkPlayerAsync(player);
        }
    }

    public void checkPlayerAsync(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long totalSeconds = store.readTotalPlaytimeSeconds(uuid);
                int lastTier = store.lastClaimedTier(uuid);
                int grantedThrough = processMilestones(player, totalSeconds, lastTier);
                if (grantedThrough > lastTier) {
                    store.setLastClaimedTier(uuid, grantedThrough);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Playtime reward check failed for " + player.getName(), ex);
            }
        });
    }

    private int processMilestones(Player player, long totalSeconds, int lastTier) {
        int grantedThrough = lastTier;
        for (Milestone milestone : milestones) {
            if (milestone.tier() <= lastTier) {
                continue;
            }
            if (totalSeconds < milestone.thresholdSeconds()) {
                break;
            }
            if (!payMilestone(player, milestone)) {
                plugin.getLogger().warning(
                        "Playtime grant failed for " + player.getName() + " (tier " + milestone.tier() + ")");
                break;
            }
            grantedThrough = milestone.tier();
        }
        return grantedThrough;
    }

    private boolean payMilestone(Player player, Milestone milestone) {
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            plugin.getLogger().warning(
                    "Playtime grant skipped — treasury unavailable for " + player.getName());
            return false;
        }
        RootMcIncomeSweepResult sweep = treasury.payPlaytimeReward(
                player.getUniqueId(),
                player.getName(),
                milestone.gold(),
                "tier=" + milestone.tier() + ";" + milestone.label());
        if (sweep == null) {
            return false;
        }
        String msg = plugin.rawMsg("playtime-grant")
                .replace("{gold}", String.valueOf(milestone.gold()))
                .replace("{milestone}", milestone.label());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(plugin.msg(msg));
            }
        });
        return true;
    }

    public PlaytimeStatus status(UUID uuid) throws Exception {
        long total = store.readTotalPlaytimeSeconds(uuid);
        int lastTier = store.lastClaimedTier(uuid);
        Milestone next = null;
        for (Milestone milestone : milestones) {
            if (milestone.tier() > lastTier) {
                next = milestone;
                break;
            }
        }
        String tierLabel = lastTier < 0
                ? "none"
                : milestones.stream()
                        .filter(m -> m.tier() == lastTier)
                        .map(Milestone::label)
                        .findFirst()
                        .orElse("tier " + lastTier);
        return new PlaytimeStatus(total, lastTier, tierLabel, next);
    }

    public record PlaytimeStatus(long totalSeconds, int lastClaimedTier, String tierLabel, Milestone next) {

        public boolean allClaimed() {
            return next == null;
        }

        public String playedLabel() {
            return PlaytimeMilestones.formatDuration(totalSeconds);
        }

        public String remainingLabel() {
            return next == null ? "" : PlaytimeMilestones.formatRemaining(totalSeconds, next.thresholdSeconds());
        }
    }
}
