package com.rootrecord.minecraft.rootessentials.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory pending confirmations for economy commands (e.g. /pay reserve). */
public final class EconomyPlayerState {

    public record PendingReserveDonation(double amountGold, long createdAtMs) {}

    private final Map<UUID, PendingReserveDonation> pendingReserveDonations = new ConcurrentHashMap<>();

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
