package com.rootrecord.minecraft.rootranks.listener;

import com.rootrecord.minecraft.rootranks.RootRanksPlugin;
import com.rootrecord.minecraft.rootranks.service.ChatPrefixService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Catalog-style chat when TownyChat is not present (Claims).
 * Matches Towny global vibe: {@code ◆ [J]Rank Name » message}.
 */
public final class CatalogChatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final RootRanksPlugin plugin;
    private final boolean enabled;
    private final String prefixTemplate;

    public CatalogChatListener(RootRanksPlugin plugin, boolean enabled, String prefixTemplate) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.prefixTemplate = prefixTemplate == null || prefixTemplate.isBlank()
                ? "&6◆&r {joint}{rank}&f{name}&8 »&f "
                : prefixTemplate;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled || townyChatPresent()) {
            return;
        }
        Player player = event.getPlayer();
        Component prefix = buildPrefix(player);
        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.empty().append(prefix).append(message));
    }

    private Component buildPrefix(Player player) {
        String joint = resolveJoint(player);
        String rank = resolveRank(player);
        // rank / joint may already contain § from ChatColor / PAPI — normalize to & for one colorize pass
        String raw = prefixTemplate
                .replace("{joint}", toAmpersand(joint))
                .replace("{rank}", toAmpersand(rank))
                .replace("{name}", player.getName())
                .replace("{message}", "");
        return LEGACY.deserialize(plugin.colorize(raw));
    }

    private String resolveRank(Player player) {
        ChatPrefixService prefixes = plugin.chatPrefixes();
        if (prefixes == null) {
            return "";
        }
        try {
            return prefixes.chatLabel(player.getUniqueId());
        } catch (Exception ex) {
            return "";
        }
    }

    private String resolveJoint(Player player) {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return resolveJointDirect(player);
        }
        try {
            String tagged = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%roothaste_tag%");
            if (tagged == null || tagged.isBlank()) {
                tagged = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%rootjoint_tag%");
            }
            if (tagged == null || tagged.isBlank()) {
                tagged = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%roottorch_tag%");
            }
            return tagged == null ? "" : tagged;
        } catch (Throwable ex) {
            return resolveJointDirect(player);
        }
    }

    /** Soft fallback when PAPI is missing — Root-Haste holder tag. */
    private String resolveJointDirect(Player player) {
        Plugin joint = Bukkit.getPluginManager().getPlugin("Root-Haste");
        if (joint == null || !joint.isEnabled()) {
            joint = Bukkit.getPluginManager().getPlugin("Root-Joint");
        }
        if (joint == null || !joint.isEnabled()) {
            return "";
        }
        try {
            Object sessions = joint.getClass().getMethod("sessions").invoke(joint);
            if (sessions == null) {
                return "";
            }
            Boolean holder = (Boolean) sessions.getClass()
                    .getMethod("isHolder", Player.class)
                    .invoke(sessions, player);
            if (holder == null || !holder) {
                return "";
            }
            Object tag = sessions.getClass().getMethod("chatTagRaw").invoke(sessions);
            return tag == null ? "" : String.valueOf(tag);
        } catch (ReflectiveOperationException ex) {
            return "";
        }
    }

    private static boolean townyChatPresent() {
        Plugin tc = Bukkit.getPluginManager().getPlugin("TownyChat");
        return tc != null && tc.isEnabled();
    }

    private static String toAmpersand(String colored) {
        if (colored == null || colored.isEmpty()) {
            return "";
        }
        return colored.replace('§', '&');
    }
}
