package com.rootrecord.minecraft.rootclaims.listener;

import com.rootrecord.minecraft.rootclaims.ClaimRecord;
import com.rootrecord.minecraft.rootclaims.ClaimStore;
import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimProtectionListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final RootClaimsPlugin plugin;
    private final ClaimStore store;
    private final Map<UUID, Long> denyCooldown = new HashMap<>();
    private final Map<UUID, String> lastZoneByPlayer = new HashMap<>();

    public ClaimProtectionListener(RootClaimsPlugin plugin, ClaimStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.notificationsEnabled() || event.getTo() == null) {
            return;
        }
        boolean xzChanged = event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()
                || !event.getFrom().getWorld().equals(event.getTo().getWorld());
        boolean floorCrossed = (event.getFrom().getBlockY() >= ClaimRecord.MINING_FLOOR_Y)
                != (event.getTo().getBlockY() >= ClaimRecord.MINING_FLOOR_Y);
        if (!xzChanged && !floorCrossed) {
            return;
        }
        String key = zoneKey(event.getTo());
        String previous = lastZoneByPlayer.put(event.getPlayer().getUniqueId(), key);
        if (key.equals(previous)) {
            return;
        }
        if (previous == null) {
            return;
        }
        Player player = event.getPlayer();
        if ("wild".equals(key) && previous.startsWith("territory:")) {
            actionBar(player, plugin.rawMsg("territory-leave").replace("{owner}", zoneOwner(previous)));
            return;
        }
        if ("wild".equals(key) && previous.startsWith("claim:")) {
            actionBar(player, plugin.rawMsg("claim-leave")
                    .replace("{owner}", zoneOwner(previous))
                    .replace("{buffer}", String.valueOf(plugin.territoryBufferBlocks())));
            return;
        }
        announceZoneEnter(player, key);
    }

    private String zoneKey(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "none";
        }
        if (loc.getBlockY() < ClaimRecord.MINING_FLOOR_Y) {
            return "underworld";
        }
        ClaimRecord claim = store.containing(loc);
        if (claim != null) {
            return "claim:" + claim.ownerId() + ":" + safeName(claim.ownerName());
        }
        ClaimRecord territory = store.territoryContaining(loc, plugin.territoryBufferBlocks());
        if (territory != null) {
            return "territory:" + territory.ownerId() + ":" + safeName(territory.ownerName());
        }
        return "wild";
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "someone" : name;
    }

    private static String zoneOwner(String zone) {
        if (zone == null) {
            return "someone";
        }
        int first = zone.indexOf(':');
        if (first < 0) {
            return "someone";
        }
        int second = zone.indexOf(':', first + 1);
        if (second < 0 || second + 1 >= zone.length()) {
            return "someone";
        }
        return zone.substring(second + 1);
    }

    private void announceZoneEnter(Player player, String key) {
        if ("underworld".equals(key)) {
            actionBar(player, plugin.rawMsg("underworld-enter"));
            return;
        }
        if (key.startsWith("claim:")) {
            actionBar(player, plugin.rawMsg("claim-enter").replace("{owner}", zoneOwner(key)));
            return;
        }
        if (key.startsWith("territory:")) {
            actionBar(player, plugin.rawMsg("territory-enter").replace("{owner}", zoneOwner(key)));
            return;
        }
        if ("wild".equals(key)) {
            actionBar(player, plugin.rawMsg("wild-enter"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }
        ClaimRecord claim = claim(event.getEntity());
        if (claim != null && !claim.mobsAllowed()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileEnterClaim(EntityMoveEvent event) {
        if (!(event.getEntity() instanceof Enemy hostile)) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        ClaimRecord to = store.containing(event.getTo());
        if (to != null && !to.mobsAllowed()) {
            hostile.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileTeleportIntoClaim(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof Enemy hostile) || event.getTo() == null) {
            return;
        }
        ClaimRecord claim = store.containing(event.getTo());
        if (claim != null && !claim.mobsAllowed()) {
            event.setCancelled(true);
            hostile.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !isHostile(event.getEntity())) {
            return;
        }
        ClaimRecord claim = store.containing(player.getLocation());
        if (claim == null || claim.mobsAllowed()) {
            return;
        }
        // Block aggro into the claim circle only — do not delete mobs in the territory band.
        event.setCancelled(true);
        ClaimRecord mobClaim = store.containing(event.getEntity().getLocation());
        if (mobClaim != null && !mobClaim.mobsAllowed()) {
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ClaimRecord claim = claim(event.getBlock());
        if (claim != null && !canUse(event.getPlayer(), claim)) {
            event.setCancelled(true);
            deny(event.getPlayer(), claim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ClaimRecord claim = claim(event.getBlockPlaced());
        if (claim != null && !canUse(event.getPlayer(), claim)) {
            event.setCancelled(true);
            deny(event.getPlayer(), claim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        ClaimRecord claim = claim(block);
        if (claim == null || canUse(event.getPlayer(), claim)) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL || shouldProtectInteraction(block)) {
            event.setCancelled(true);
            deny(event.getPlayer(), claim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.protectBuckets()) {
            return;
        }
        ClaimRecord claim = claim(event.getBlockClicked().getRelative(event.getBlockFace()));
        if (claim != null && !canUse(event.getPlayer(), claim)) {
            event.setCancelled(true);
            deny(event.getPlayer(), claim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.protectBuckets()) {
            return;
        }
        ClaimRecord claim = claim(event.getBlockClicked());
        if (claim != null && !canUse(event.getPlayer(), claim)) {
            event.setCancelled(true);
            deny(event.getPlayer(), claim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (!plugin.protectBuckets()) {
            return;
        }
        ClaimRecord to = claim(event.getToBlock());
        ClaimRecord from = claim(event.getBlock());
        if (to != null && from != to) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.protectEntities()) {
            return;
        }
        ClaimRecord claim = claim(event.getRightClicked());
        if (claim != null && !canUse(event.getPlayer(), claim)) {
            event.setCancelled(true);
            deny(event.getPlayer(), claim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!plugin.protectEntities()) {
            return;
        }
        ClaimRecord claim = claim(event.getRightClicked());
        if (claim != null && !canUse(event.getPlayer(), claim)) {
            event.setCancelled(true);
            deny(event.getPlayer(), claim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Creeper
                && event.getEntity() instanceof Player player
                && store.containing(player.getLocation()) != null) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && isHostileDamage(event.getDamager())) {
            ClaimRecord claim = store.containing(player.getLocation());
            if (claim != null && !claim.mobsAllowed()) {
                event.setCancelled(true);
                return;
            }
        }
        if (!plugin.protectEntities()) {
            return;
        }
        ClaimRecord claim = claim(event.getEntity());
        Player attacker = resolvePlayer(event.getDamager());
        if (claim != null && (attacker == null || !canUse(attacker, claim))) {
            event.setCancelled(true);
            if (attacker != null) {
                deny(attacker, claim);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!plugin.protectEntities()) {
            return;
        }
        ClaimRecord claim = claim(event.getEntity());
        if (claim != null && !canUse(event.getPlayer(), claim)) {
            event.setCancelled(true);
            deny(event.getPlayer(), claim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!plugin.protectEntities()) {
            return;
        }
        ClaimRecord claim = claim(event.getEntity());
        Player remover = resolvePlayer(event.getRemover());
        if (claim != null && (remover == null || !canUse(remover, claim))) {
            event.setCancelled(true);
            if (remover != null) {
                deny(remover, claim);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!plugin.protectFire()) {
            return;
        }
        ClaimRecord claim = claim(event.getBlock());
        Player player = event.getPlayer();
        if (claim != null && (player == null || !canUse(player, claim))) {
            event.setCancelled(true);
            if (player != null) {
                deny(player, claim);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.protectFire() && claim(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.protectExplosions()) {
            event.blockList().removeIf(block -> claim(block) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.protectExplosions()) {
            return;
        }
        if (event.getEntity() instanceof Creeper && explosionTouchesClaim(event)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Creeper && plugin.regenerateCreeperDamage()) {
            List<RegeneratingBlock> blocks = new ArrayList<>();
            event.blockList().removeIf(block -> {
                BlockState state = block.getState();
                return state instanceof TileState || state instanceof InventoryHolder;
            });
            for (Block block : event.blockList()) {
                blocks.add(new RegeneratingBlock(block.getLocation(), block.getBlockData().clone()));
            }
            event.setYield(0);
            scheduleCreeperRegeneration(blocks);
            return;
        }
        event.blockList().removeIf(block -> claim(block) != null);
    }

    private void scheduleCreeperRegeneration(List<RegeneratingBlock> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        blocks.sort(Comparator.comparingInt(block -> block.location().getBlockY()));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (RegeneratingBlock snapshot : blocks) {
                Block current = snapshot.location().getBlock();
                if (current.getType().isAir() || current.getType() == Material.FIRE) {
                    current.setBlockData(snapshot.data(), false);
                }
            }
        }, plugin.creeperRegenerationDelayTicks());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.protectPistons()) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (claim(block) != null || claim(block.getRelative(event.getDirection())) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!plugin.protectPistons()) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (claim(block) != null || claim(block.getRelative(event.getDirection())) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private ClaimRecord claim(Block block) {
        if (!plugin.enabledFlag() || block == null) {
            return null;
        }
        return store.containing(block);
    }

    private ClaimRecord claim(Entity entity) {
        if (!plugin.enabledFlag() || entity == null) {
            return null;
        }
        if (entity instanceof Player) {
            return null;
        }
        return store.containing(entity.getLocation());
    }

    private boolean canUse(Player player, ClaimRecord claim) {
        if (player == null) {
            return false;
        }
        return player.isOp()
                || player.hasPermission("rootclaims.bypass")
                || player.hasPermission("rootclaims.admin")
                || player.hasPermission("group.admin")
                || store.areaRoot(claim).canManage(player.getUniqueId());
    }

    private boolean shouldProtectInteraction(Block block) {
        Material type = block.getType();
        if (plugin.protectContainers() && block.getState() instanceof InventoryHolder) {
            return true;
        }
        if (!plugin.protectInteractables()) {
            return false;
        }
        String name = type.name();
        return name.contains("DOOR")
                || name.contains("TRAPDOOR")
                || name.contains("FENCE_GATE")
                || name.contains("BUTTON")
                || name.contains("PRESSURE_PLATE")
                || name.contains("LEVER")
                || name.contains("REDSTONE")
                || name.contains("CHEST")
                || name.contains("BARREL")
                || name.contains("SHULKER")
                || name.contains("FURNACE")
                || name.contains("HOPPER")
                || name.contains("DROPPER")
                || name.contains("DISPENSER")
                || name.contains("BREWING_STAND")
                || name.contains("ANVIL")
                || name.contains("BEACON")
                || name.contains("LECTERN")
                || name.contains("COMPOSTER")
                || name.contains("CAULDRON")
                || name.contains("CAMPFIRE")
                || name.contains("BELL")
                || name.contains("CRAFTING_TABLE")
                || name.contains("SMITHING_TABLE")
                || name.contains("ENCHANTING_TABLE")
                || name.contains("JUKEBOX")
                || name.contains("NOTE_BLOCK")
                || name.contains("CHISELED_BOOKSHELF");
    }

    private void deny(Player player, ClaimRecord claim) {
        long now = System.currentTimeMillis();
        Long last = denyCooldown.get(player.getUniqueId());
        if (last != null && now - last < 1200L) {
            return;
        }
        denyCooldown.put(player.getUniqueId(), now);
        player.sendMessage(plugin.msg("denied").replace("{owner}", claim.ownerName()));
    }

    private void actionBar(Player player, String raw) {
        player.sendActionBar(LEGACY.deserialize(plugin.colorize(raw)));
    }

    private boolean explosionTouchesClaim(EntityExplodeEvent event) {
        if (claim(event.getEntity()) != null) {
            return true;
        }
        for (Block block : event.blockList()) {
            if (claim(block) != null) {
                return true;
            }
        }
        return false;
    }

    private static Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        if (entity instanceof Hanging) {
            return null;
        }
        return null;
    }

    private record RegeneratingBlock(Location location, BlockData data) {}

    private static boolean isHostileDamage(Entity damager) {
        if (isHostile(damager)) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Enemy;
        }
        return false;
    }

    /** Hostiles for claim mobs-off — includes phantoms/ghasts ({@link Enemy}), not only {@code Monster}. */
    private static boolean isHostile(Entity entity) {
        return entity instanceof Enemy;
    }
}
