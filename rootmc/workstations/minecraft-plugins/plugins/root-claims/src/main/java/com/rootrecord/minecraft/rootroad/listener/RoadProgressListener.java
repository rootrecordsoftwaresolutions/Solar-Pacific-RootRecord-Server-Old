package com.rootrecord.minecraft.rootroad.listener;

import com.rootrecord.minecraft.rootroad.RoadGuideService;
import com.rootrecord.minecraft.rootroad.RootRoadPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;

public final class RoadProgressListener implements Listener {

    private final RootRoadPlugin plugin;
    private final RoadGuideService guide;

    public RoadProgressListener(RootRoadPlugin plugin, RoadGuideService guide) {
        this.plugin = plugin;
        this.guide = guide;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.enabledFlag()) {
            return;
        }
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message == null || message.length() < 2) {
            return;
        }
        String body = message.substring(1).trim().toLowerCase(Locale.ROOT);
        String[] parts = body.split("\\s+");
        if (parts.length == 0) {
            return;
        }
        String cmd = parts[0];
        if (cmd.equals("rtp") || cmd.equals("wild")) {
            guide.markRtpCommand(player);
            return;
        }
        if (cmd.equals("loan") && parts.length >= 2 && parts[1].equals("take")) {
            guide.onLoanCommand(player, body);
            return;
        }
        if (cmd.equals("claim") || cmd.equals("claims") || cmd.equals("c") || cmd.equals("rootclaims")) {
            guide.onClaimCommand(player, body);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.enabledFlag()) {
            return;
        }
        guide.onTeleport(event.getPlayer());
    }
}
