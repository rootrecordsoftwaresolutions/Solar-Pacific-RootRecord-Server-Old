package com.rootrecord.minecraft.rootspawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

public final class SpawnProtectionListener implements Listener {

    private final RootSpawnPlugin plugin;
    private BukkitTask mobCleanup;

    public SpawnProtectionListener(RootSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    public void startMobCleanup() {
        stopMobCleanup();
        long ticks = plugin.config().mobCleanupTicks();
        mobCleanup = Bukkit.getScheduler().runTaskTimer(plugin, this::purgeMobs, ticks, ticks);
    }

    public void stopMobCleanup() {
        if (mobCleanup != null) {
            mobCleanup.cancel();
            mobCleanup = null;
        }
    }

    private void purgeMobs() {
        SpawnBoundary boundary = plugin.boundary();
        if (boundary == null || boundary.isEmpty()) {
            return;
        }
        var world = Bukkit.getWorld(boundary.worldName());
        if (world == null) {
            return;
        }
        double[] c = boundary.centroidXZ();
        double r = boundary.boundingRadius();
        Location center = new Location(world, c[0], 128, c[1]);
        for (Entity entity : world.getNearbyEntities(center, r, 256, r)) {
            if (entity instanceof Player || !(entity instanceof LivingEntity)) {
                continue;
            }
            if (plugin.isMobFreeZone(entity.getLocation())
                    && !plugin.isInsideChamberSpawnVolume(entity.getLocation())
                    && !plugin.isChamberMobArea(entity.getLocation())) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (canBuild(event.getPlayer())) {
            return;
        }
        if (plugin.isGriefProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (canBuild(event.getPlayer())) {
            return;
        }
        if (plugin.isGriefProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Location loc = event.getLocation();
        if (plugin.isInsideChamberSpawnVolume(loc) || plugin.isChamberMobArea(loc)) {
            return;
        }
        if (plugin.isMobFreeZone(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            if (event.getEntity() instanceof Monster
                    && plugin.isMobFreeZone(event.getEntity().getLocation())
                    && !plugin.isInsideChamberSpawnVolume(event.getEntity().getLocation())
                    && !plugin.isChamberMobArea(event.getEntity().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (plugin.shouldProtectPlayerFromDamage(victim, event)) {
            event.setCancelled(true);
            victim.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null && plugin.shouldProtectPlayerFromDamage(attacker, victim)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.shouldProtectPlayerFromDamage(victim, event)) {
            event.setCancelled(true);
            Entity damager = event.getDamager();
            if (damager instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static boolean canBuild(Player player) {
        return player != null && (player.isOp()
                || player.hasPermission("rootspawn.bypass")
                || player.hasPermission("group.admin"));
    }
}
