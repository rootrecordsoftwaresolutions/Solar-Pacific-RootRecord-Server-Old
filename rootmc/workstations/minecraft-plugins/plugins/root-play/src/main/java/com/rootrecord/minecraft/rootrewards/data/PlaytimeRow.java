package com.rootrecord.minecraft.rootrewards.data;

import java.util.UUID;

public record PlaytimeRow(UUID uuid, String username, long totalSeconds) {

    public String displayName() {
        if (username != null && !username.isBlank()) {
            return username;
        }
        return uuid != null ? uuid.toString().substring(0, 8) : "?";
    }
}
