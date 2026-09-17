package com.rootrecord.minecraft.rootreferrals.service;

import com.rootrecord.minecraft.common.RootMcPublicReachout;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

/** Treasury payouts and public reachout for referral rewards. */
public final class RewardService {

    private final JavaPlugin plugin;

    public RewardService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean payReferrer(UUID recipientUuid, String recipientName, double amount, long edgeId, String reason) {
        return pay(recipientUuid, recipientName, amount, edgeId, reason, "referral");
    }

    public boolean payMilestone(UUID recipientUuid, String recipientName, double amount, String milestoneKey) {
        return pay(recipientUuid, recipientName, amount, null, "referral:milestone:" + milestoneKey, "referral");
    }

    private boolean pay(
            UUID recipientUuid,
            String recipientName,
            double amount,
            Long edgeId,
            String reason,
            String reachoutCategory) {
        if (amount <= 0 || recipientUuid == null) {
            return true;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            return false;
        }
        String name = recipientName == null || recipientName.isBlank() ? "player" : recipientName;
        boolean ok = treasury.grantToPlayer(
                recipientUuid,
                name,
                amount,
                treasury.treasuryUuid(),
                treasury.treasuryUsername(),
                reason);
        if (!ok) {
            return false;
        }
        RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
        if (reachout != null) {
            reachout.recordTreasuryOutflow(reachoutCategory, name, recipientUuid, amount, true);
        }
        return true;
    }

    public void notifyOnline(UUID uuid, String message) {
        if (uuid == null || message == null || message.isBlank()) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            online.sendMessage(message);
        }
    }

    public static String formatGold(double g) {
        if (Math.abs(g - Math.rint(g)) < 1e-9) {
            return String.valueOf((long) Math.rint(g));
        }
        return String.format(Locale.US, "%.2f", g);
    }
}
