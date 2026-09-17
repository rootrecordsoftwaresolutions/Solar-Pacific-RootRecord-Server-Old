package com.rootrecord.minecraft.rootreferrals.service;

import com.rootrecord.minecraft.rootreferrals.ReferralsConfig;
import com.rootrecord.minecraft.rootreferrals.RootReferralsPlugin;
import com.rootrecord.minecraft.rootreferrals.hook.LinkHook;
import com.rootrecord.minecraft.rootreferrals.hook.PlaytimeHook;
import com.rootrecord.minecraft.rootreferrals.store.ReferralStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.UUID;

/** Qualify pending referral edges and pay rewards. */
public final class QualifyService {

    private final RootReferralsPlugin plugin;
    private final ReferralsConfig config;
    private final ReferralStore store;
    private final LinkHook linkHook;
    private final PlaytimeHook playtimeHook;
    private final RewardService rewardService;

    public QualifyService(
            RootReferralsPlugin plugin,
            ReferralsConfig config,
            ReferralStore store,
            LinkHook linkHook,
            PlaytimeHook playtimeHook,
            RewardService rewardService) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.linkHook = linkHook;
        this.playtimeHook = playtimeHook;
        this.rewardService = rewardService;
    }

    public void processPending() {
        if (!store.ready()) {
            return;
        }
        for (ReferralStore.ReferralEdge edge : store.pendingEdges()) {
            tryQualify(edge);
        }
    }

    public boolean forceQualify(UUID recruitUuid) {
        Optional<ReferralStore.ReferralEdge> edgeOpt = store.findEdgeByRecruit(recruitUuid);
        if (edgeOpt.isEmpty()) {
            return false;
        }
        ReferralStore.ReferralEdge edge = edgeOpt.get();
        if (ReferralStore.STATUS_QUALIFIED.equals(edge.status())) {
            return true;
        }
        return qualifyEdge(edge);
    }

    private void tryQualify(ReferralStore.ReferralEdge edge) {
        if (edge == null || !ReferralStore.STATUS_PENDING.equals(edge.status())) {
            return;
        }
        LinkHook.LinkInfo recruitLink = linkHook.linkInfo(edge.recruitUuid());
        if (!recruitLink.discordLinked()) {
            return;
        }
        if (config.requireReferrerLinked()) {
            LinkHook.LinkInfo referrerLink = linkHook.linkInfo(edge.referrerUuid());
            if (!referrerLink.discordLinked()) {
                return;
            }
        }
        long playtime = playtimeHook.totalSeconds(edge.recruitUuid());
        if (playtime < config.playtimeSeconds()) {
            return;
        }
        if (config.minActiveDays() > 0) {
            int activeDays = playtimeHook.activeDays(edge.recruitUuid());
            if (activeDays < config.minActiveDays()) {
                return;
            }
        }
        qualifyEdge(edge);
    }

    private boolean qualifyEdge(ReferralStore.ReferralEdge edge) {
        if (ReferralStore.STATUS_QUALIFIED.equals(edge.status())) {
            return true;
        }
        if (!ReferralStore.STATUS_PENDING.equals(edge.status())) {
            return false;
        }
        if (!store.markQualified(edge.id())) {
            return false;
        }
        if (!config.rewardsEnabled()) {
            store.incrementQualifiedAndEarned(edge.referrerUuid(), 0);
            return true;
        }
        double referrerPay = config.qualifyReferrerG();
        double recruitPay = config.qualifyRecruitG();
        Optional<ReferralStore.ReferralPlayer> referrerOpt = store.findPlayer(edge.referrerUuid());
        if (referrerOpt.isEmpty()) {
            return false;
        }
        ReferralStore.ReferralPlayer referrer = referrerOpt.get();
        double capRemaining = Math.max(0, config.lifetimeCapG() - referrer.totalEarnedG());
        if (referrerPay > capRemaining) {
            referrerPay = capRemaining;
        }
        double totalPaid = 0;
        if (referrerPay > 0) {
            String referrerName = displayName(edge.referrerUuid(), referrer.displayName());
            boolean paid = rewardService.payReferrer(
                    edge.referrerUuid(),
                    referrerName,
                    referrerPay,
                    edge.id(),
                    "referral:qualify");
            if (paid) {
                totalPaid += referrerPay;
                store.recordPayout(edge.referrerUuid(), edge.id(), "qualify", referrerPay, "referral:qualify");
                rewardService.notifyOnline(
                        edge.referrerUuid(),
                        plugin.msg("qualified-referrer")
                                .replace("{player}", displayName(edge.recruitUuid(), "recruit"))
                                .replace("{amount}", RewardService.formatGold(referrerPay)));
            } else {
                rewardService.notifyOnline(edge.referrerUuid(), plugin.msg("no-treasury"));
            }
        }
        if (recruitPay > 0) {
            String recruitName = displayName(edge.recruitUuid(), "recruit");
            boolean paid = rewardService.payReferrer(
                    edge.recruitUuid(),
                    recruitName,
                    recruitPay,
                    edge.id(),
                    "referral:qualify-recruit");
            if (paid) {
                totalPaid += recruitPay;
                store.recordPayout(edge.recruitUuid(), edge.id(), "qualify-recruit", recruitPay, "referral:qualify-recruit");
                rewardService.notifyOnline(
                        edge.recruitUuid(),
                        plugin.msg("qualified-recruit")
                                .replace("{referrer}", referrer.displayName()));
            }
        }
        store.incrementQualifiedAndEarned(edge.referrerUuid(), totalPaid);
        checkMilestones(edge.referrerUuid());
        return true;
    }

    private void checkMilestones(UUID referrerUuid) {
        for (ReferralsConfig.Milestone milestone : config.milestones()) {
            Optional<ReferralStore.ReferralPlayer> playerOpt = store.findPlayer(referrerUuid);
            if (playerOpt.isEmpty()) {
                return;
            }
            ReferralStore.ReferralPlayer player = playerOpt.get();
            if (player.totalQualified() < milestone.qualified()) {
                continue;
            }
            String key = milestone.key();
            if (store.hasMilestone(referrerUuid, key)) {
                continue;
            }
            double capRemaining = Math.max(0, config.lifetimeCapG() - player.totalEarnedG());
            double amount = Math.min(milestone.rewardG(), capRemaining);
            if (amount <= 0) {
                store.markMilestonePaid(referrerUuid, key);
                continue;
            }
            String name = displayName(referrerUuid, player.displayName());
            boolean paid = rewardService.payMilestone(referrerUuid, name, amount, key);
            if (paid) {
                store.markMilestonePaid(referrerUuid, key);
                store.recordPayout(referrerUuid, null, "milestone", amount, "referral:milestone:" + key);
                store.addEarned(referrerUuid, amount);
                rewardService.notifyOnline(
                        referrerUuid,
                        plugin.msg("milestone")
                                .replace("{count}", String.valueOf(milestone.qualified()))
                                .replace("{amount}", RewardService.formatGold(amount)));
                player = store.findPlayer(referrerUuid).orElse(player);
            }
        }
    }

    private String displayName(UUID uuid, String fallback) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return fallback == null || fallback.isBlank() ? uuid.toString().substring(0, 8) : fallback;
    }
}
