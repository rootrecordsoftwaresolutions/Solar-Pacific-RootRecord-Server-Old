package com.rootrecord.minecraft.rootspawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
        mobCleanup = Bukkit.getScheduler().runTaskTimer(plugin.host(), this::purgeMobs, ticks, ticks);
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
            // Multiverse namespaced alias
            for (var w : Bukkit.getWorlds()) {
                if (SpawnBoundary.worldsMatch(w, boundary.worldName())) {
                    world = w;
                    break;
                }
            }
        }
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
            if (plugin.isMobFreeZone(entity.getLocation())) {
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
    public void onInteract(PlayerInteractEvent event) {
        if (canBuild(event.getPlayer())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (!plugin.isGriefProtected(block.getLocation())) {
            return;
        }
        // Block switches, buttons, doors, chests, lecterns, etc. in spawn.
        Material type = block.getType();
        if (action == Action.PHYSICAL || isInteractable(type)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (canBuild(event.getPlayer())) {
            return;
        }
        if (plugin.isGriefProtected(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (canBuild(event.getPlayer())) {
            return;
        }
        if (plugin.isGriefProtected(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (canBuild(event.getPlayer())) {
            return;
        }
        if (plugin.isGriefProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (canBuild(event.getPlayer())) {
            return;
        }
        if (plugin.isGriefProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = event.getRemover() instanceof Player p ? p : null;
        if (canBuild(player)) {
            return;
        }
        if (plugin.isGriefProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (plugin.isMobFreeZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            if (event.getEntity() instanceof Monster
                    && plugin.isMobFreeZone(event.getEntity().getLocation())) {
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

    /** Spawn grief/interact bypass — operators only. */
    private static boolean canBuild(Player player) {
        return player != null && player.isOp();
    }

    private static boolean isInteractable(Material type) {
        if (type == null || type.isAir()) {
            return false;
        }
        String name = type.name();
        return type == Material.LEVER
                || type == Material.LECTERN
                || type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL
                || type == Material.ENDER_CHEST
                || type == Material.HOPPER
                || type == Material.DROPPER
                || type == Material.DISPENSER
                || type == Material.FURNACE
                || type == Material.BLAST_FURNACE
                || type == Material.SMOKER
                || type == Material.CRAFTING_TABLE
                || type == Material.ENCHANTING_TABLE
                || type == Material.ANVIL
                || type == Material.CHIPPED_ANVIL
                || type == Material.DAMAGED_ANVIL
                || type == Material.BREWING_STAND
                || type == Material.BEACON
                || type == Material.JUKEBOX
                || type == Material.NOTE_BLOCK
                || type == Material.DAYLIGHT_DETECTOR
                || type == Material.COMPARATOR
                || type == Material.REPEATER
                || type == Material.CAKE
                || type == Material.RESPAWN_ANCHOR
                || type == Material.BELL
                || name.endsWith("_BUTTON")
                || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_PRESSURE_PLATE")
                || name.contains("SHULKER_BOX")
                || name.contains("SIGN")
                || name.endsWith("_BED")
                || name.contains("CAMPFIRE")
                || name.contains("CANDLE");
    }
}
