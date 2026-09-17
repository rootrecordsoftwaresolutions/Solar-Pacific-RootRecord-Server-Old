package com.rootrecord.minecraft.rootessentials.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStateService {

    public record TpaRequest(UUID requester, UUID target, boolean here, long createdAt) {}

    private final Map<UUID, TpaRequest> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastMessagePartner = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> afk = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> vanished = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> socialSpy = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> god = new ConcurrentHashMap<>();
    private volatile boolean globalChatMuted;

    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> tpAuto = new ConcurrentHashMap<>();
    private final Map<UUID, PendingReserveDonation> pendingReserveDonations = new ConcurrentHashMap<>();

    public record PendingReserveDonation(double amountGold, long createdAtMs) {}

    public void offerTpa(UUID target, TpaRequest request) {
        incoming.put(target, request);
    }

    public TpaRequest pendingTpa(UUID target) {
        return incoming.get(target);
    }

    public void clearTpa(UUID target) {
        incoming.remove(target);
    }

    public void setMessagePartner(UUID a, UUID b) {
        lastMessagePartner.put(a, b);
        lastMessagePartner.put(b, a);
    }

    public UUID messagePartner(UUID player) {
        return lastMessagePartner.get(player);
    }

    public boolean toggleAfk(UUID player) {
        if (RootTimesAfkBridge.toggle(player)) {
            return RootTimesAfkBridge.isAfk(player);
        }
        boolean next = !afk.getOrDefault(player, false);
        afk.put(player, next);
        return next;
    }

    public boolean isAfk(UUID player) {
        Boolean times = RootTimesAfkBridge.isAfkOrNull(player);
        if (times != null) {
            return times;
        }
        return afk.getOrDefault(player, false);
    }

    public void clearAfk(UUID player) {
        RootTimesAfkBridge.clear(player);
        afk.remove(player);
    }

    public boolean toggleVanish(UUID player) {
        boolean next = !vanished.getOrDefault(player, false);
        vanished.put(player, next);
        return next;
    }

    public boolean isVanished(UUID player) {
        return vanished.getOrDefault(player, false);
    }

    public boolean toggleSocialSpy(UUID player) {
        boolean next = !socialSpy.getOrDefault(player, false);
        socialSpy.put(player, next);
        return next;
    }

    public boolean isSocialSpy(UUID player) {
        return socialSpy.getOrDefault(player, false);
    }

    public boolean toggleGod(UUID player) {
        boolean next = !god.getOrDefault(player, false);
        god.put(player, next);
        return next;
    }

    public boolean isGod(UUID player) {
        return god.getOrDefault(player, false);
    }

    public boolean isGlobalChatMuted() {
        return globalChatMuted;
    }

    public void setGlobalChatMuted(boolean muted) {
        globalChatMuted = muted;
    }

    public void ignore(UUID player, UUID target) {
        ignores.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(target);
    }

    public void unignore(UUID player, UUID target) {
        Set<UUID> set = ignores.get(player);
        if (set != null) set.remove(target);
    }

    public boolean isIgnoring(UUID player, UUID target) {
        Set<UUID> set = ignores.get(player);
        return set != null && set.contains(target);
    }

    public Set<UUID> ignored(UUID player) {
        return ignores.getOrDefault(player, Set.of());
    }

    public boolean toggleTpAuto(UUID player) {
        boolean next = !tpAuto.getOrDefault(player, false);
        tpAuto.put(player, next);
        return next;
    }

    public boolean isTpAuto(UUID player) {
        return tpAuto.getOrDefault(player, false);
    }

    public void offerReserveDonation(UUID player, double amountGold) {
        if (player == null || amountGold <= 0) {
            return;
        }
        pendingReserveDonations.put(player, new PendingReserveDonation(amountGold, System.currentTimeMillis()));
    }

    public PendingReserveDonation pendingReserveDonation(UUID player) {
        return pendingReserveDonations.get(player);
    }

    public void clearReserveDonation(UUID player) {
        pendingReserveDonations.remove(player);
    }
}
