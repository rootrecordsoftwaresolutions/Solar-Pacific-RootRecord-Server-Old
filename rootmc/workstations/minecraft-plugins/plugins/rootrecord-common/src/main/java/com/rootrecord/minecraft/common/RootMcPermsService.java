package com.rootrecord.minecraft.common;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** First-party permissions — groups, tracks, effective nodes (replaces LuckPerms for Root suite). */
public interface RootMcPermsService {

    boolean hasGroup(UUID playerId, String groupId);

    boolean grantGroup(UUID playerId, String groupId);

    boolean revokeGroup(UUID playerId, String groupId);

    /** Highest index in {@code trackOrder} the player holds, or {@code -1}. */
    int highestTrackIndex(UUID playerId, List<String> trackOrder);

    boolean has(UUID playerId, String permission);

    void refresh(Player player);

    Set<String> groupsOf(UUID playerId);

    String groupDisplay(String groupId);

    String groupPrefix(String groupId);

    boolean ensureUser(UUID playerId, String username);
}
