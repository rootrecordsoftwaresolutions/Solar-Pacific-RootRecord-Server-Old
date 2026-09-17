package com.rootrecord.minecraft.rootspawn;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class SpawnZoneListener implements Listener {

    private final RootSpawnPlugin plugin;

    public SpawnZoneListener(RootSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (event.getFrom().getX() == to.getX()
                && event.getFrom().getY() == to.getY()
                && event.getFrom().getZ() == to.getZ()) {
            return;
        }
        checkTransition(event.getPlayer(), event.getFrom(), to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        checkTransition(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // no state to clear
    }

    private void checkTransition(Player player, Location from, Location to) {
        SpawnBoundary boundary = plugin.boundary();
        if (boundary == null || boundary.isEmpty()) {
            return;
        }
        boolean wasInside = plugin.isInsideSafeZone(from);
        boolean nowInside = plugin.isInsideSafeZone(to);
        if (wasInside == nowInside) {
            return;
        }
        if (nowInside) {
            onEnter(player);
        } else {
            onLeave(player);
        }
    }

    private void onEnter(Player player) {
        SpawnConfig cfg = plugin.config();
        plugin.actionBar(player, cfg.enterActionBar());
        player.setFireTicks(0);
        clearCombatTargets(player);
    }

    private void onLeave(Player player) {
        SpawnConfig cfg = plugin.config();
        plugin.actionBar(player, cfg.leaveActionBar());
    }

    private void clearCombatTargets(Player player) {
        for (LivingEntity le : player.getWorld().getLivingEntities()) {
            if (le.equals(player) || !(le instanceof Mob mob)) {
                continue;
            }
            if (mob.getTarget() != null && mob.getTarget().equals(player)) {
                mob.setTarget(null);
            }
        }
    }
}
