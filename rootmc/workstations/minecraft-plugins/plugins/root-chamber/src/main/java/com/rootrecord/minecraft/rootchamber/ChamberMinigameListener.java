package com.rootrecord.minecraft.rootchamber;

import com.rootrecord.minecraft.rootspawn.RootSpawnPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChamberMinigameListener implements Listener {

    private final RootSpawnPlugin spawn;
    private final ChamberMinigameManager minigame;

    ChamberMinigameListener(RootSpawnPlugin spawn, ChamberMinigameManager minigame) {
        this.spawn = spawn;
        this.minigame = minigame;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        boolean wasChamber = spawn.isInsideChamber(event.getFrom());
        boolean nowChamber = spawn.isInsideChamber(event.getTo());
        boolean wasWell = spawn.isInsideWellFootprint(event.getFrom());
        boolean nowWell = spawn.isInsideWellFootprint(event.getTo());

        if (nowWell && !wasWell) {
            minigame.onEnterWell(player);
        }
        if (nowChamber && !wasChamber) {
            minigame.onEnterChamber(player);
        } else if (wasChamber && !nowChamber) {
            minigame.onLeaveChamber(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        minigame.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        minigame.onPlayerDeath(event.getEntity());
    }
}
