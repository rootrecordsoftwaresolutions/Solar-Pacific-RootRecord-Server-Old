package com.rootrecord.minecraft.common;

import java.util.UUID;

/**
 * Listing-vote streak perk (Root-Play): 5 votes in 24h halves transaction tax and death loss.
 */
public interface RootMcVotePerk {

    int requiredVotes();

    int votesInWindow(UUID playerId);

    boolean active(UUID playerId);

    /** 0.5 while active, else 1.0. */
    double taxMultiplier(UUID playerId);

    /** 0.5 while active, else 1.0. */
    double deathFeeMultiplier(UUID playerId);

    /** Epoch millis when the rolling window would drop below {@link #requiredVotes()}, or 0. */
    long expiresAtEpochMs(UUID playerId);

    void invalidate(UUID playerId);
}
