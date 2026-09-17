package com.rootrecord.minecraft.rootranks.service;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Splits LuckPerms prefixes into player-track rank (Explorer → Champion) and
 * optional staff/donor badges so staff still show their purchased rank.
 */
public final class ChatPrefixService {

    private static final Map<String, Integer> PLAYER_TRACK = Map.ofEntries(
            Map.entry("default", 10),
            Map.entry("wanderer", 12),
            Map.entry("settler", 14),
            Map.entry("pioneer", 16),
            Map.entry("citizen", 18),
            Map.entry("veteran", 20),
            Map.entry("elite", 22),
            Map.entry("champion", 24));

    private static final Map<String, Integer> BADGE_TRACK = Map.ofEntries(
            Map.entry("supporter", 35),
            Map.entry("patron", 38),
            Map.entry("pro", 40),
            Map.entry("benefactor", 45),
            Map.entry("lifetime", 50),
            Map.entry("founder", 55),
            Map.entry("helper", 80),
            Map.entry("moderator", 85),
            Map.entry("admin", 100),
            Map.entry("dev", 110),
            Map.entry("developer", 110),
            Map.entry("owner", 120));

    private final LuckPerms luckPerms;

    public ChatPrefixService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public String playerPrefix(UUID playerId) {
        return resolve(playerId).playerPrefix();
    }

    public String badgePrefix(UUID playerId) {
        return resolve(playerId).badgePrefix();
    }

    public String combinedPrefix(UUID playerId) {
        Resolved resolved = resolve(playerId);
        return resolved.playerPrefix() + resolved.badgePrefix();
    }

    public String playerRankName(UUID playerId) {
        return resolve(playerId).playerRankName();
    }

    private Resolved resolve(UUID playerId) {
        User user = luckPerms.getUserManager().getUser(playerId);
        if (user == null) {
            return explorerDefault();
        }

        String bestPlayerGroup = "default";
        int bestPlayerWeight = 0;
        String bestBadgeGroup = null;
        int bestBadgeWeight = 0;

        for (Group group : user.getInheritedGroups(QueryOptions.nonContextual())) {
            String name = group.getName().toLowerCase(Locale.ROOT);
            Integer playerWeight = PLAYER_TRACK.get(name);
            if (playerWeight != null && playerWeight >= bestPlayerWeight) {
                bestPlayerWeight = playerWeight;
                bestPlayerGroup = name;
            }
            Integer badgeWeight = BADGE_TRACK.get(name);
            if (badgeWeight != null && badgeWeight >= bestBadgeWeight) {
                bestBadgeWeight = badgeWeight;
                bestBadgeGroup = name;
            }
        }

        String playerPrefix = prefixForGroup(bestPlayerGroup);
        String badgePrefix = bestBadgeGroup == null ? "" : prefixForGroup(bestBadgeGroup);
        String rankName = displayNameForGroup(bestPlayerGroup);
        return new Resolved(playerPrefix, badgePrefix, rankName);
    }

    private Resolved explorerDefault() {
        String prefix = prefixForGroup("default");
        String rankName = displayNameForGroup("default");
        return new Resolved(prefix, "", rankName);
    }

    private String prefixForGroup(String groupName) {
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return "";
        }
        String raw = group.getCachedData().getMetaData().getPrefix();
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private String displayNameForGroup(String groupName) {
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return "Explorer";
        }
        String display = group.getDisplayName();
        if (display == null || display.isBlank()) {
            return groupName.substring(0, 1).toUpperCase(Locale.ROOT) + groupName.substring(1);
        }
        return display;
    }

    private record Resolved(String playerPrefix, String badgePrefix, String playerRankName) {}
}
