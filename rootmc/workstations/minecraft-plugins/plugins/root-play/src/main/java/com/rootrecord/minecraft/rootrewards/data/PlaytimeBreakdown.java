package com.rootrecord.minecraft.rootrewards.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Network total plus per-server scopes (towny / claims). */
public record PlaytimeBreakdown(UUID uuid, String username, long totalSeconds, Map<String, Long> byServer) {

    public PlaytimeBreakdown {
        byServer = byServer == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(byServer));
    }

    public String displayName() {
        if (username != null && !username.isBlank()) {
            return username;
        }
        return uuid != null ? uuid.toString().substring(0, 8) : "?";
    }

    public long serverSeconds(String scope) {
        if (scope == null) {
            return 0L;
        }
        return byServer.getOrDefault(scope.trim().toLowerCase(), 0L);
    }
}
