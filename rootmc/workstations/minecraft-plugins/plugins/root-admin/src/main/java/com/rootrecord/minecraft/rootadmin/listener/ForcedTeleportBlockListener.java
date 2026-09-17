package com.rootrecord.minecraft.rootadmin.listener;

import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Blocks forced teleport chat commands for everyone (including OP/staff).
 * Leaves consent teleports ({@code /tpa}, {@code /tpahere}, accept/deny) alone.
 */
public final class ForcedTeleportBlockListener implements Listener {

    private static final Set<String> BLOCKED = Set.of(
            "tp",
            "teleport",
            "tphere",
            "minecraft:tp",
            "minecraft:teleport");

    private final RootAdminPlugin plugin;

    public ForcedTeleportBlockListener(RootAdminPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.isBlank() || raw.charAt(0) != '/') {
            return;
        }
        String withoutSlash = raw.substring(1).trim();
        if (withoutSlash.isEmpty()) {
            return;
        }
        int space = withoutSlash.indexOf(' ');
        String label = (space < 0 ? withoutSlash : withoutSlash.substring(0, space))
                .toLowerCase(Locale.ROOT);
        if (!BLOCKED.contains(label)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.msg("forced-tp-disabled"));
    }
}
