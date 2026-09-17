package com.rootrecord.minecraft.rootrewards.service;

import com.rootrecord.minecraft.common.RootMcVotePerk;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootrewards.data.RewardsStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling 24h listing-vote perk: 5 votes → half transaction tax + half death loss.
 */
public final class VotePerkService implements RootMcVotePerk {

    public static final int REQUIRED = 5;
    public static final Duration WINDOW = Duration.ofHours(24);
    public static final double MULTIPLIER = 0.5d;
    private static final long CACHE_MS = 15_000L;

    public record Snapshot(int votes, int required, boolean active, Instant expiresAt) {
        public static Snapshot none() {
            return new Snapshot(0, REQUIRED, false, null);
        }

        public String remainingLabel() {
            if (!active || expiresAt == null) {
                return "";
            }
            return VoteSiteCooldown.formatRemaining(Duration.between(Instant.now(), expiresAt));
        }

        public String announceLabel() {
            if (active) {
                String left = remainingLabel();
                return left.isBlank() ? "½ tax + ½ death active" : "½ tax + ½ death · " + left + " left";
            }
            return votes + "/" + required + " votes toward ½ tax perk";
        }
    }

    private final RootRewardsPlugin plugin;
    private final ConcurrentHashMap<UUID, Snapshot> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> cacheAt = new ConcurrentHashMap<>();

    public VotePerkService(RootRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    public Snapshot snapshot(UUID playerId) {
        if (playerId == null) {
            return Snapshot.none();
        }
        Long at = cacheAt.get(playerId);
        Snapshot hit = cache.get(playerId);
        Instant now = Instant.now();
        if (hit != null && at != null && System.currentTimeMillis() - at < CACHE_MS) {
            if (!hit.active() || hit.expiresAt() == null || !now.isAfter(hit.expiresAt())) {
                return hit;
            }
        }
        Snapshot fresh = load(playerId);
        cache.put(playerId, fresh);
        cacheAt.put(playerId, System.currentTimeMillis());
        return fresh;
    }

    private Snapshot load(UUID playerId) {
        RewardsStore store = plugin.store();
        if (store == null) {
            return Snapshot.none();
        }
        try {
            Instant since = Instant.now().minus(WINDOW);
            List<Instant> times = store.voteTimesSince(playerId, since);
            int votes = times.size();
            if (votes < REQUIRED) {
                return new Snapshot(votes, REQUIRED, false, null);
            }
            Instant fifthNewest = times.get(REQUIRED - 1);
            Instant expires = fifthNewest.plus(WINDOW);
            if (!expires.isAfter(Instant.now())) {
                return new Snapshot(votes, REQUIRED, false, null);
            }
            return new Snapshot(votes, REQUIRED, true, expires);
        } catch (Exception ex) {
            plugin.getLogger().warning("Vote perk lookup failed: " + ex.getMessage());
            return Snapshot.none();
        }
    }

    @Override
    public int requiredVotes() {
        return REQUIRED;
    }

    @Override
    public int votesInWindow(UUID playerId) {
        return snapshot(playerId).votes();
    }

    @Override
    public boolean active(UUID playerId) {
        return snapshot(playerId).active();
    }

    @Override
    public double taxMultiplier(UUID playerId) {
        return active(playerId) ? MULTIPLIER : 1.0d;
    }

    @Override
    public double deathFeeMultiplier(UUID playerId) {
        return active(playerId) ? MULTIPLIER : 1.0d;
    }

    @Override
    public long expiresAtEpochMs(UUID playerId) {
        Instant expires = snapshot(playerId).expiresAt();
        return expires == null ? 0L : expires.toEpochMilli();
    }

    @Override
    public void invalidate(UUID playerId) {
        if (playerId == null) {
            return;
        }
        cache.remove(playerId);
        cacheAt.remove(playerId);
    }
}
