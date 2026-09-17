package com.rootrecord.minecraft.rootskills.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks temporary ability flags / durations per player. */
public final class AbilitySessions {

    private final Map<UUID, Map<String, Long>> until = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Double>> flags = new ConcurrentHashMap<>();

    public void start(UUID playerId, String abilityId, long durationMs) {
        if (playerId == null || abilityId == null) {
            return;
        }
        until.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(abilityId.toLowerCase(), System.currentTimeMillis() + Math.max(0L, durationMs));
    }

    public boolean has(UUID playerId, String abilityId) {
        if (playerId == null || abilityId == null) {
            return false;
        }
        Map<String, Long> map = until.get(playerId);
        if (map == null) {
            return false;
        }
        Long end = map.get(abilityId.toLowerCase());
        if (end == null) {
            return false;
        }
        if (System.currentTimeMillis() > end) {
            map.remove(abilityId.toLowerCase());
            return false;
        }
        return true;
    }

    public void setFlag(UUID playerId, String key, double value, long durationMs) {
        start(playerId, key, durationMs);
        flags.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(key.toLowerCase(), value);
    }

    public double flag(UUID playerId, String key, double def) {
        if (!has(playerId, key)) {
            return def;
        }
        Map<String, Double> map = flags.get(playerId);
        if (map == null) {
            return def;
        }
        return map.getOrDefault(key.toLowerCase(), def);
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            until.remove(playerId);
            flags.remove(playerId);
        }
    }
}
