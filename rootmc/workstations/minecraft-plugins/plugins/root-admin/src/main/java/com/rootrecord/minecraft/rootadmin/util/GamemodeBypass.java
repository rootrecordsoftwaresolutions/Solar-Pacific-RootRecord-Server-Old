package com.rootrecord.minecraft.rootadmin.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One-shot bypass for GamemodeGuardListener when the server console sets gamemode. */
public final class GamemodeBypass {

    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private GamemodeBypass() {}

    public static void allowNextChange(UUID playerId) {
        if (playerId != null) {
            PENDING.add(playerId);
        }
    }

    public static boolean consume(UUID playerId) {
        return playerId != null && PENDING.remove(playerId);
    }
}
