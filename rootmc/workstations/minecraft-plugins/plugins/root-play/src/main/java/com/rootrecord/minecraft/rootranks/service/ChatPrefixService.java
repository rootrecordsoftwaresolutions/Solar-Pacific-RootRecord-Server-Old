package com.rootrecord.minecraft.rootranks.service;

import com.rootrecord.minecraft.common.RootMcPermsService;
import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Splits prefixes into player-track rank (Explorer → Champion) and optional staff badges.
 * Chat label always uses the personal player rank (staff badges stay on {@link #badgePrefix}).
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

    private static final Map<String, String> LABEL_COLOR = Map.ofEntries(
            Map.entry("default", "&7"),
            Map.entry("wanderer", "&a"),
            Map.entry("settler", "&2"),
            Map.entry("pioneer", "&b"),
            Map.entry("citizen", "&9"),
            Map.entry("veteran", "&3"),
            Map.entry("elite", "&d"),
            Map.entry("champion", "&6"),
            Map.entry("supporter", "&d"),
            Map.entry("patron", "&d"),
            Map.entry("pro", "&d"),
            Map.entry("benefactor", "&d"),
            Map.entry("lifetime", "&d"),
            Map.entry("founder", "&6"),
            Map.entry("helper", "&e"),
            Map.entry("moderator", "&c"),
            Map.entry("admin", "&c"),
            Map.entry("dev", "&b"),
            Map.entry("developer", "&b"),
            Map.entry("owner", "&c"));

    private final RootMcPermsService perms;

    public ChatPrefixService(RootMcPermsService perms) {
        this.perms = perms;
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

    /**
     * Single chat label: personal player rank (Explorer → Champion), plus {@code [Pro]} when the
     * player holds the Pro membership group. Staff groups like Admin/Mod do not replace the rank.
     * Example: {@code Wanderer [Pro] }.
     */
    public String chatLabel(UUID playerId) {
        Resolved r = resolve(playerId);
        String label = colorize(colorFor(r.playerGroup()) + r.playerRankName() + " ");
        if (perms.hasGroup(playerId, "pro")) {
            String pro = colorize(perms.groupPrefix("pro"));
            if (pro == null || pro.isBlank()) {
                pro = colorize("&8[&bPro&8]&r ");
            }
            label = label + pro;
        }
        return label;
    }

    public String playerRankName(UUID playerId) {
        return resolve(playerId).playerRankName();
    }

    private Resolved resolve(UUID playerId) {
        String bestPlayerGroup = "default";
        int bestPlayerWeight = 0;
        String bestBadgeGroup = null;
        int bestBadgeWeight = 0;

        for (String name : perms.groupsOf(playerId)) {
            String key = name.toLowerCase(Locale.ROOT);
            Integer playerWeight = PLAYER_TRACK.get(key);
            if (playerWeight != null && playerWeight >= bestPlayerWeight) {
                bestPlayerWeight = playerWeight;
                bestPlayerGroup = key;
            }
            Integer badgeWeight = BADGE_TRACK.get(key);
            if (badgeWeight != null && badgeWeight >= bestBadgeWeight) {
                bestBadgeWeight = badgeWeight;
                bestBadgeGroup = key;
            }
        }

        String playerPrefix = colorize(perms.groupPrefix(bestPlayerGroup));
        String badgePrefix = bestBadgeGroup == null ? "" : colorize(perms.groupPrefix(bestBadgeGroup));
        String rankName = display(bestPlayerGroup);
        return new Resolved(bestPlayerGroup, bestBadgeGroup, playerPrefix, badgePrefix, rankName);
    }

    private String display(String groupName) {
        String d = perms.groupDisplay(groupName);
        if (d == null || d.isBlank()) {
            if ("default".equals(groupName)) {
                return "Explorer";
            }
            return groupName.substring(0, 1).toUpperCase(Locale.ROOT) + groupName.substring(1);
        }
        return d;
    }

    private static String colorFor(String group) {
        return LABEL_COLOR.getOrDefault(group == null ? "" : group.toLowerCase(Locale.ROOT), "&7");
    }

    private static String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private record Resolved(
            String playerGroup,
            String badgeGroup,
            String playerPrefix,
            String badgePrefix,
            String playerRankName) {}
}
