package com.rootrecord.minecraft.roothaste.listener;

import com.rootrecord.minecraft.roothaste.RootHastePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Fallback chat [J] tag when PlaceholderAPI / Towny format is not used. */
public final class JointChatListener implements Listener {

    private final RootHastePlugin plugin;

    public JointChatListener(RootHastePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        // Claims / no TownyChat: Root-Play CatalogChatListener owns format (includes [J]).
        if (plugin.getServer().getPluginManager().getPlugin("TownyChat") == null) {
            return;
        }
        if (plugin.sessions() == null || !plugin.sessions().isHolder(event.getPlayer())) {
            return;
        }
        String tag = plugin.sessions().chatTagColored();
        if (tag == null || tag.isBlank()) {
            return;
        }
        String format = event.getFormat();
        if (format != null && (format.contains("[J]") || format.contains("[T]"))) {
            return;
        }
        event.setFormat(tag + format);
    }
}
