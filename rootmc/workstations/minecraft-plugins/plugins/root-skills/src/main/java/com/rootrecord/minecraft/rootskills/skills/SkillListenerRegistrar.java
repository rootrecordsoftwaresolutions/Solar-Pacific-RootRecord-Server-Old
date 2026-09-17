package com.rootrecord.minecraft.rootskills.skills;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.rootrecord.minecraft.rootskills.api.SkillId;

public final class SkillListenerRegistrar implements Listener {

    private static final String META_SPAWN_REASON = "rootskills_spawn_reason";

    private final RootSkillsPlugin plugin;
    private final Map<String, Long> recentPlaced = new ConcurrentHashMap<>();
    private final Map<UUID, Double> elytraDistance = new ConcurrentHashMap<>();

    public SkillListenerRegistrar(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Skill listeners registered");
    }

    public void register(Listener listener) {
        if (listener != null) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("anti-farm.track-recent-blocks", true)
                && !plugin.getConfig().getBoolean("anti-farm.prevent-place-and-break", true)) {
            return;
        }
        Block b = event.getBlockPlaced();
        recentPlaced.put(blockKey(b), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        event.getEntity().setMetadata(META_SPAWN_REASON,
                new FixedMetadataValue(plugin, event.getSpawnReason().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!canEarn(player) || deniedRegion(player)) {
            return;
        }
        Block block = event.getBlock();
        if (isRecentlyPlaced(block)) {
            return;
        }
        Material type = block.getType();
        SkillId skill = skillForBlock(type);
        if (skill == null || !plugin.skillCatalog().enabled(skill)) {
            return;
        }
        long xp = plugin.skillCatalog().require(skill).xpForBlock(type);
        if (xp <= 0) {
            xp = xpForBlock(type);
        }
        if (plugin.abilitySessions() != null && plugin.abilitySessions().has(player.getUniqueId(), "break_bonus")) {
            if (Math.random() < 0.25) {
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(type));
            }
        }
        plugin.xpService().addXp(player.getUniqueId(), skill, xp);
        plugin.triggerBus().fire(player, "on_block_break", Map.of("material", type.name(), "skill", skill.key()));
        if (skill == SkillId.HERBALISM) {
            plugin.triggerBus().fire(player, "on_crop_harvest", Map.of("material", type.name()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player player = damagerPlayer(event);
        if (player == null || !canEarn(player) || deniedRegion(player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (spawnerZero(event.getEntity())) {
            return;
        }
        SkillId skill = skillForWeapon(player);
        if (skill == null || !plugin.skillCatalog().enabled(skill)) {
            return;
        }
        long base = plugin.skillCatalog().require(skill).combatHitXp();
        long xp = Math.max(base, (long) Math.ceil(event.getFinalDamage()));
        plugin.xpService().addXp(player.getUniqueId(), skill, xp);
        plugin.triggerBus().fire(player, "on_entity_damage", Map.of("skill", skill.key()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !canEarn(killer) || deniedRegion(killer)) {
            return;
        }
        if (spawnerZero(event.getEntity())) {
            return;
        }
        SkillId skill = skillForWeapon(killer);
        if (skill == null) {
            skill = SkillId.UNARMED;
        }
        if (!plugin.skillCatalog().enabled(skill)) {
            return;
        }
        long xp = plugin.skillCatalog().require(skill).combatKillXp();
        plugin.xpService().addXp(killer.getUniqueId(), skill, Math.max(1L, xp));
        plugin.triggerBus().fire(killer, "on_entity_kill", Map.of("skill", skill.key()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!canEarn(player) || deniedRegion(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            boolean skipAcro = plugin.getConfig().getBoolean("anti-farm.prevent-acrobatics-afk", true)
                    && event.getFinalDamage() < 1.0;
            if (!skipAcro) {
                long xp = Math.max(
                        plugin.skillCatalog().require(SkillId.ACROBATICS).actionXp(),
                        (long) Math.ceil(event.getFinalDamage() * 2));
                plugin.xpService().addXp(player.getUniqueId(), SkillId.ACROBATICS, xp);
                plugin.triggerBus().fire(player, "on_fall", Map.of());
            }
        }
        if (event.getFinalDamage() > 0 && plugin.skillCatalog().enabled(SkillId.DEFENSE)) {
            long defXp = Math.max(
                    plugin.skillCatalog().require(SkillId.DEFENSE).actionXp(),
                    (long) Math.ceil(event.getFinalDamage()));
            plugin.xpService().addXp(player.getUniqueId(), SkillId.DEFENSE, defXp);
            plugin.triggerBus().fire(player, "on_player_damage", Map.of());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (!canEarn(player) || deniedRegion(player)) {
            return;
        }
        plugin.xpService().addXp(
                player.getUniqueId(),
                SkillId.FISHING,
                plugin.skillCatalog().require(SkillId.FISHING).actionXp());
        plugin.triggerBus().fire(player, "on_fish_catch", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        // Best-effort: award nearest player in radius
        Location loc = event.getBlock().getLocation();
        Player nearest = nearestPlayer(loc, 5);
        if (nearest == null || !canEarn(nearest) || deniedRegion(nearest)) {
            return;
        }
        if (plugin.getConfig().getBoolean("anti-farm.prevent-alchemy-exploit", true)
                && isRecentlyPlaced(event.getBlock())) {
            return;
        }
        plugin.xpService().addXp(
                nearest.getUniqueId(),
                SkillId.ALCHEMY,
                plugin.skillCatalog().require(SkillId.ALCHEMY).actionXp());
        plugin.triggerBus().fire(nearest, "on_brew", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) {
            return;
        }
        if (!canEarn(player) || deniedRegion(player)) {
            return;
        }
        plugin.xpService().addXp(
                player.getUniqueId(),
                SkillId.TAMING,
                plugin.skillCatalog().require(SkillId.TAMING).actionXp());
        plugin.triggerBus().fire(player, "on_tame", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        Location loc = event.getBlock().getLocation();
        Player nearest = nearestPlayer(loc, 5);
        if (nearest == null || !canEarn(nearest) || deniedRegion(nearest)) {
            return;
        }
        plugin.xpService().addXp(
                nearest.getUniqueId(),
                SkillId.SMELTING,
                plugin.skillCatalog().require(SkillId.SMELTING).actionXp());
        plugin.triggerBus().fire(nearest, "on_smelt", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        // Light repair XP when result has higher durability potential — award small XP
        if (!canEarn(player)) {
            return;
        }
        plugin.xpService().addXp(
                player.getUniqueId(),
                SkillId.REPAIR,
                plugin.skillCatalog().require(SkillId.REPAIR).actionXp());
        plugin.triggerBus().fire(player, "on_repair", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrindstone(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.GRINDSTONE) {
            return;
        }
        if (event.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.RESULT) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir() || !canEarn(player) || deniedRegion(player)) {
            return;
        }
        if (!plugin.skillCatalog().enabled(SkillId.SALVAGE)) {
            return;
        }
        plugin.xpService().addXp(
                player.getUniqueId(),
                SkillId.SALVAGE,
                plugin.skillCatalog().require(SkillId.SALVAGE).actionXp());
        plugin.triggerBus().fire(player, "on_salvage", Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) {
            return;
        }
        double mult = plugin.getConfig().getDouble("anti-farm.breeding-multiplier", 1.0);
        if (mult <= 0 || !canEarn(player)) {
            return;
        }
        plugin.xpService().addXp(player.getUniqueId(), SkillId.TAMING, Math.max(1L, (long) (15 * mult)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding() || event.getTo() == null || event.getFrom() == null) {
            return;
        }
        if (!canEarn(player)) {
            return;
        }
        double dist = event.getFrom().distance(event.getTo());
        if (dist <= 0) {
            return;
        }
        double acc = elytraDistance.merge(player.getUniqueId(), dist, Double::sum);
        if (acc >= 10.0) {
            elytraDistance.put(player.getUniqueId(), acc - 10.0);
            plugin.xpService().addXp(
                    player.getUniqueId(),
                    SkillId.ELYTRA,
                    plugin.skillCatalog().require(SkillId.ELYTRA).actionXp());
            plugin.triggerBus().fire(player, "on_elytra_boost", Map.of());
        }
    }

    private boolean canEarn(Player player) {
        return player != null
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR
                && player.hasPermission("rootskills.use");
    }

    private boolean deniedRegion(Player player) {
        return RegionGuard.isDenied(plugin, player);
    }

    private boolean isRecentlyPlaced(Block block) {
        if (!plugin.getConfig().getBoolean("anti-farm.prevent-place-and-break", true)
                && !plugin.getConfig().getBoolean("anti-farm.track-recent-blocks", true)) {
            return false;
        }
        String key = blockKey(block);
        Long when = recentPlaced.get(key);
        if (when == null) {
            return false;
        }
        long ttl = plugin.getConfig().getLong("anti-farm.recent-block-ttl-seconds", 30) * 1000L;
        if (System.currentTimeMillis() - when > ttl) {
            recentPlaced.remove(key);
            return false;
        }
        return true;
    }

    private boolean spawnerZero(Entity entity) {
        double mult = plugin.getConfig().getDouble("anti-farm.mobspawners-multiplier", 0.0);
        if (mult > 0) {
            return false;
        }
        if (!entity.hasMetadata(META_SPAWN_REASON)) {
            return false;
        }
        String reason = entity.getMetadata(META_SPAWN_REASON).getFirst().asString();
        if ("SPAWNER".equals(reason) || "SPAWNER_EGG".equals(reason)) {
            return true;
        }
        if ("NETHER_PORTAL".equals(reason)
                && plugin.getConfig().getDouble("anti-farm.nether-portal-multiplier", 0.0) <= 0) {
            return true;
        }
        return "EGG".equals(reason)
                && plugin.getConfig().getDouble("anti-farm.eggs-multiplier", 0.0) <= 0;
    }

    private static String blockKey(Block b) {
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private static Player nearestPlayer(Location loc, double radius) {
        Player best = null;
        double bestDist = radius * radius;
        for (Player p : loc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static Player damagerPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            return p;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    private static SkillId skillForWeapon(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Material m = hand == null ? Material.AIR : hand.getType();
        String n = m.name();
        if (n.endsWith("_SWORD")) {
            return SkillId.SWORDS;
        }
        if (n.endsWith("_AXE")) {
            return SkillId.AXES;
        }
        if (m == Material.BOW) {
            return SkillId.ARCHERY;
        }
        if (m == Material.CROSSBOW) {
            return SkillId.CROSSBOWS;
        }
        if (m == Material.TRIDENT) {
            return SkillId.TRIDENTS;
        }
        if (m == Material.MACE) {
            return SkillId.MACES;
        }
        if (n.contains("SPEAR")) {
            return SkillId.SPEARS;
        }
        if (m == Material.AIR) {
            return SkillId.UNARMED;
        }
        return SkillId.UNARMED;
    }

    private static SkillId skillForBlock(Material type) {
        if (isWoodcutting(type)) {
            return SkillId.WOODCUTTING;
        }
        if (isCrop(type)) {
            return SkillId.HERBALISM;
        }
        if (isDirt(type)) {
            return SkillId.EXCAVATION;
        }
        if (isMining(type)) {
            return SkillId.MINING;
        }
        return null;
    }

    private static boolean isWoodcutting(Material type) {
        String n = type.name();
        return n.endsWith("_LOG")
                || n.endsWith("_STEM")
                || n.endsWith("_WOOD")
                || n.endsWith("_HYPHAE")
                || n.equals("MANGROVE_ROOTS")
                || type == Material.SHROOMLIGHT
                || type == Material.RED_MUSHROOM_BLOCK
                || type == Material.BROWN_MUSHROOM_BLOCK
                || type == Material.MUSHROOM_STEM;
    }

    private static boolean isDirt(Material type) {
        return switch (type) {
            case DIRT, GRASS_BLOCK, SAND, RED_SAND, GRAVEL, CLAY, SOUL_SAND, SOUL_SOIL,
                 MYCELIUM, PODZOL, MUD, MUDDY_MANGROVE_ROOTS, COARSE_DIRT, ROOTED_DIRT,
                 SNOW, SNOW_BLOCK -> true;
            default -> false;
        };
    }

    private static boolean isMining(Material type) {
        String n = type.name();
        if (n.contains("ORE")
                || n.contains("DEEPSLATE")
                || n.contains("TERRACOTTA")
                || n.contains("SCULK")
                || n.contains("AMETHYST")
                || n.contains("CORAL_BLOCK")) {
            return true;
        }
        return switch (type) {
            case ANCIENT_DEBRIS, STONE, COBBLESTONE, NETHERRACK, END_STONE, OBSIDIAN,
                 DEEPSLATE, COBBLED_DEEPSLATE, BLACKSTONE, BASALT, SMOOTH_BASALT,
                 TUFF, CALCITE, DRIPSTONE_BLOCK, POINTED_DRIPSTONE,
                 ANDESITE, POLISHED_ANDESITE, DIORITE, POLISHED_DIORITE,
                 GRANITE, POLISHED_GRANITE, DEEPSLATE_BRICKS, DEEPSLATE_TILES,
                 CRACKED_DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_TILES,
                 GLOWSTONE, MAGMA_BLOCK, GILDED_BLACKSTONE,
                 QUARTZ_BLOCK, QUARTZ_BRICKS, QUARTZ_PILLAR, SMOOTH_QUARTZ,
                 CHISELED_QUARTZ_BLOCK,
                 SANDSTONE, RED_SANDSTONE, PRISMARINE, DARK_PRISMARINE,
                 BRICKS, NETHER_BRICKS, END_STONE_BRICKS,
                 MOSSY_COBBLESTONE, MOSSY_STONE_BRICKS,
                 PACKED_ICE, BLUE_ICE -> true;
            default -> false;
        };
    }

    private static boolean isCrop(Material type) {
        String n = type.name().toLowerCase(Locale.ROOT);
        return type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES
                || type == Material.BEETROOTS || type == Material.NETHER_WART || type == Material.COCOA
                || type == Material.SUGAR_CANE || type == Material.CACTUS || type == Material.MELON
                || type == Material.PUMPKIN || type == Material.BAMBOO || type == Material.SWEET_BERRY_BUSH
                || n.contains("mushroom") || type == Material.KELP || type == Material.KELP_PLANT;
    }

    private static long xpForBlock(Material type) {
        String n = type.name();
        if (n.contains("DIAMOND") || n.contains("EMERALD") || type == Material.ANCIENT_DEBRIS) {
            return 40L;
        }
        if (n.contains("GOLD") || n.contains("IRON") || n.contains("LAPIS") || n.contains("REDSTONE")
                || n.contains("COPPER") || n.contains("QUARTZ") || n.contains("COAL")) {
            return 15L;
        }
        if (isWoodcutting(type)) {
            return 8L;
        }
        if (isCrop(type)) {
            return 6L;
        }
        if (isDirt(type)) {
            return 4L;
        }
        return 5L;
    }
}
