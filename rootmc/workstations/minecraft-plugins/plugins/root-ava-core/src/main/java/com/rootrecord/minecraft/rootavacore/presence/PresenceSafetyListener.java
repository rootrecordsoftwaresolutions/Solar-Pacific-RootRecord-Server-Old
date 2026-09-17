package com.rootrecord.minecraft.rootavacore.presence;

import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Phase 1 rails — no PVP, no steal, no grief interaction with Ava's body.
 */
public final class PresenceSafetyListener implements Listener {

    private final RootAvaCorePlugin plugin;
    private final AvaPresenceService presence;

    public PresenceSafetyListener(RootAvaCorePlugin plugin, AvaPresenceService presence) {
        this.plugin = plugin;
        this.presence = presence;
    }

    private boolean isBody(Entity entity) {
        return presence.isAvaBody(entity);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!isBody(event.getEntity())) return;
        if (plugin.config().presence().invulnerable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        if (isBody(event.getEntity()) || isBody(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (isBody(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isBody(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (isBody(event.getTarget()) || isBody(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isBody(event.getRightClicked())) return;
        // Menu listener opens GUI; still block equipment fiddling here.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isBody(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
