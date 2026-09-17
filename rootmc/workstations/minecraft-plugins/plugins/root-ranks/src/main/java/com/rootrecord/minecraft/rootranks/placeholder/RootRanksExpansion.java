package com.rootrecord.minecraft.rootranks.placeholder;

import com.rootrecord.minecraft.rootranks.RootRanksPlugin;
import com.rootrecord.minecraft.rootranks.service.ChatPrefixService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.UUID;

public final class RootRanksExpansion extends PlaceholderExpansion {

    private final RootRanksPlugin plugin;
    private final ChatPrefixService prefixes;

    public RootRanksExpansion(RootRanksPlugin plugin, ChatPrefixService prefixes) {
        this.plugin = plugin;
        this.prefixes = prefixes;
    }

    @Override
    public String getIdentifier() {
        return "rootranks";
    }

    @Override
    public String getAuthor() {
        return "Root Record";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        UUID uuid = player.getUniqueId();
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT);
        try {
            return switch (key) {
                case "prefix", "player_prefix" -> prefixes.playerPrefix(uuid);
                case "badge", "staff_prefix", "badge_prefix" -> prefixes.badgePrefix(uuid);
                case "combined", "chat_prefix" -> prefixes.combinedPrefix(uuid);
                case "rank", "player_rank", "display" -> prefixes.playerRankName(uuid);
                default -> "";
            };
        } catch (Exception ex) {
            return "";
        }
    }
}
