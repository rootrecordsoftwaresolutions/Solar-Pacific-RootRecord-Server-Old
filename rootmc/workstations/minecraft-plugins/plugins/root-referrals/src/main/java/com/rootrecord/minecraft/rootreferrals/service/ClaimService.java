package com.rootrecord.minecraft.rootreferrals.service;

import com.rootrecord.minecraft.rootreferrals.ReferralsConfig;
import com.rootrecord.minecraft.rootreferrals.RootReferralsPlugin;
import com.rootrecord.minecraft.rootreferrals.hook.LinkHook;
import com.rootrecord.minecraft.rootreferrals.store.ReferralStore;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ClaimService {

    public enum ClaimResult {
        SUCCESS,
        SELF,
        ALREADY,
        UNKNOWN,
        WINDOW,
        NOT_LINKED,
        FAILED
    }

    private final RootReferralsPlugin plugin;
    private final ReferralsConfig config;
    private final ReferralStore store;
    private final LinkHook linkHook;
    private final CodeService codeService;

    public ClaimService(
            RootReferralsPlugin plugin,
            ReferralsConfig config,
            ReferralStore store,
            LinkHook linkHook,
            CodeService codeService) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.linkHook = linkHook;
        this.codeService = codeService;
    }

    public ClaimResult claim(Player recruit, String rawCode) {
        if (recruit == null || rawCode == null || rawCode.isBlank()) {
            return ClaimResult.UNKNOWN;
        }
        if (!store.ready()) {
            return ClaimResult.FAILED;
        }
        if (store.findEdgeByRecruit(recruit.getUniqueId()).isPresent()) {
            return ClaimResult.ALREADY;
        }
        Optional<ReferralStore.ReferralPlayer> referrerOpt =
                store.findByCode(rawCode.trim().toUpperCase());
        if (referrerOpt.isEmpty()) {
            return ClaimResult.UNKNOWN;
        }
        ReferralStore.ReferralPlayer referrer = referrerOpt.get();
        if (referrer.uuid().equals(recruit.getUniqueId())) {
            return ClaimResult.SELF;
        }
        if (config.claimWithinHours() > 0) {
            long firstPlayed = recruit.getFirstPlayed();
            if (firstPlayed > 0) {
                long deadline = firstPlayed + TimeUnit.HOURS.toMillis(config.claimWithinHours());
                if (System.currentTimeMillis() > deadline) {
                    return ClaimResult.WINDOW;
                }
            }
        }
        LinkHook.LinkInfo recruitLink = linkHook.linkInfo(recruit.getUniqueId());
        if (!recruitLink.discordLinked()) {
            return ClaimResult.NOT_LINKED;
        }
        LinkHook.LinkInfo referrerLink = linkHook.linkInfo(referrer.uuid());
        String referrerDiscord = referrerLink.identity();
        String recruitDiscord = recruitLink.identity();
        if (recruitDiscord != null
                && !recruitDiscord.isBlank()
                && referrerDiscord != null
                && recruitDiscord.equalsIgnoreCase(referrerDiscord)) {
            return ClaimResult.SELF;
        }
        String claimIp = recruit.getAddress() != null ? recruit.getAddress().getAddress().getHostAddress() : null;
        boolean ipFlag = false;
        if (config.softIp() && claimIp != null && !claimIp.isBlank()) {
            ipFlag = store.referrerHasClaimIp(referrer.uuid(), claimIp);
        }
        boolean ok = store.insertPendingEdge(
                referrer.uuid(),
                recruit.getUniqueId(),
                referrerDiscord,
                recruitDiscord,
                claimIp,
                ipFlag);
        return ok ? ClaimResult.SUCCESS : ClaimResult.FAILED;
    }

    public ReferralStore.ReferralPlayer showOrCreateCode(Player player) {
        LinkHook.LinkInfo link = linkHook.linkInfo(player.getUniqueId());
        return codeService.ensureCode(player.getUniqueId(), player.getName(), link.identity());
    }
}
