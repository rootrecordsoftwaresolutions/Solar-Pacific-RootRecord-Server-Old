package com.rootrecord.minecraft.rootskills.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player talent cooldown tracker. */
public final class CooldownService {

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public boolean ready(UUID playerId, String talentId, long nowMs) {
        if (playerId == null || talentId == null) {
            return false;
        }
        Map<String, Long> map = cooldowns.get(playerId);
        if (map == null) {
            return true;
        }
        Long until = map.get(talentId);
        return until == null || nowMs >= until;
    }

    public void start(UUID playerId, String talentId, long untilMs) {
        if (playerId == null || talentId == null) {
            return;
        }
        cooldowns.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(talentId, untilMs);
    }

    public long remainingMs(UUID playerId, String talentId, long nowMs) {
        if (playerId == null || talentId == null) {
            return 0L;
        }
        Map<String, Long> map = cooldowns.get(playerId);
        if (map == null) {
            return 0L;
        }
        Long until = map.get(talentId);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - nowMs);
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            cooldowns.remove(playerId);
        }
    }
}
