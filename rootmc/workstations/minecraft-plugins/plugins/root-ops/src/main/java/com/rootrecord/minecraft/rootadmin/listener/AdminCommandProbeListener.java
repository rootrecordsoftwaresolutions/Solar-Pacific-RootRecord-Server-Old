package com.rootrecord.minecraft.rootadmin.listener;

import com.rootrecord.minecraft.rootadmin.probe.AdminCommandProbeCatalog;
import com.rootrecord.minecraft.rootadmin.probe.AdminCommandProbeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AdminCommandProbeListener implements Listener {

    private final AdminCommandProbeService probeService;

    public AdminCommandProbeListener(AdminCommandProbeService probeService) {
        this.probeService = probeService;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        AdminCommandProbeCatalog.ProbeMatch match =
                AdminCommandProbeCatalog.matchUnauthorized(player, event.getMessage());
        if (match != null) {
            probeService.recordAttempt(player, event.getMessage(), match);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        probeService.onQuit(event.getPlayer().getUniqueId());
    }
}
