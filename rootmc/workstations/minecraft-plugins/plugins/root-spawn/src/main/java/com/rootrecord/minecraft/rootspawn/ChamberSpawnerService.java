package com.rootrecord.minecraft.rootspawn;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.spawner.Spawner;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Chamber spawners ignore light; mobs they spawn stay alive inside the chamber volume. */
public final class ChamberSpawnerService implements Listener {

    private static final SpawnRule ANY_LIGHT = new SpawnRule(0, 15, 0, 15);
    private static final int SPAWN_RADIUS_XZ = 8;
    private static final int SPAWN_RADIUS_Y = 4;

    private final RootSpawnPlugin plugin;
    private final CopyOnWriteArrayList<Location> spawnerBlocks = new CopyOnWriteArrayList<>();
    private BukkitTask boostTask;
    private UUID boostPlayerId;

    public ChamberSpawnerService(RootSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    public void refreshLoadedChunks() {
        spawnerBlocks.clear();
        SpawnBoundary chamber = plugin.chamberBoundary();
        if (chamber == null || chamber.isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(chamber.worldName());
        if (world == null) {
            return;
        }
        int[] bounds = chamber.horizontalBounds();
        if (bounds == null) {
            return;
        }
        int minChunkX = floorDiv(bounds[0], 16);
        int maxChunkX = floorDiv(bounds[1], 16);
        int minChunkZ = floorDiv(bounds[2], 16);
        int maxChunkZ = floorDiv(bounds[3], 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    scanChunk(world.getChunkAt(cx, cz));
                }
            }
        }
        plugin.getLogger().info("Chamber spawners configured: " + spawnerBlocks.size());
    }

    public void boostForRun(Player player) {
        stopBoost();
        if (player == null) {
            return;
        }
        boostPlayerId = player.getUniqueId();
        refreshLoadedChunks();
        boostTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player boosted = boostPlayerId == null ? null : Bukkit.getPlayer(boostPlayerId);
            if (boosted == null || !plugin.isChamberMinigameRunning(boosted)) {
                stopBoost();
                return;
            }
            for (Location loc : spawnerBlocks) {
                BlockState state = loc.getBlock().getState();
                if (state instanceof CreatureSpawner spawner) {
                    configureSpawner(spawner);
                }
            }
        }, 0L, 100L);
    }

    public void stopBoost() {
        if (boostTask != null) {
            boostTask.cancel();
            boostTask = null;
        }
        boostPlayerId = null;
    }

    boolean isNearChamberSpawner(Location loc) {
        if (loc == null || loc.getWorld() == null || spawnerBlocks.isEmpty()) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        World world = loc.getWorld();
        for (Location spawner : spawnerBlocks) {
            if (!world.equals(spawner.getWorld())) {
                continue;
            }
            if (Math.abs(spawner.getBlockX() - x) <= SPAWN_RADIUS_XZ
                    && Math.abs(spawner.getBlockZ() - z) <= SPAWN_RADIUS_XZ
                    && Math.abs(spawner.getBlockY() - y) <= SPAWN_RADIUS_Y) {
                return true;
            }
        }
        return false;
    }

    public boolean isChamberMobArea(Location loc) {
        return plugin.isInsideChamberSpawnVolume(loc) || isNearChamberSpawner(loc);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!chunkIntersectsChamber(event.getChunk())) {
            return;
        }
        scanChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPreSpawnerSpawn(PreSpawnerSpawnEvent event) {
        Location spawnerLoc = event.getSpawnerLocation();
        if (!isChamberSpawnerBlock(spawnerLoc)) {
            return;
        }
        BlockState state = spawnerLoc.getBlock().getState();
        if (state instanceof CreatureSpawner spawner) {
            configureSpawner(spawner);
        }
        event.setCancelled(false);
        event.setShouldAbortSpawn(false);
    }

    private boolean isChamberSpawnerBlock(Location loc) {
        if (loc == null) {
            return false;
        }
        if (plugin.isInsideChamber(loc)) {
            return true;
        }
        return plugin.isInsideChamberSpawnVolume(loc) && loc.getBlock().getType() == Material.SPAWNER;
    }

    private boolean chunkIntersectsChamber(Chunk chunk) {
        SpawnBoundary chamber = plugin.chamberBoundary();
        if (chamber == null || chamber.isEmpty() || !chunk.getWorld().getName().equals(chamber.worldName())) {
            return false;
        }
        int[] bounds = chamber.horizontalBounds();
        if (bounds == null) {
            return false;
        }
        int chunkMinX = chunk.getX() * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getZ() * 16;
        int chunkMaxZ = chunkMinZ + 15;
        return bounds[1] >= chunkMinX && bounds[0] <= chunkMaxX
                && bounds[3] >= chunkMinZ && bounds[2] <= chunkMaxZ;
    }

    private void scanChunk(Chunk chunk) {
        SpawnBoundary chamber = plugin.chamberBoundary();
        if (chamber == null || chamber.isEmpty()) {
            return;
        }
        int yBottom = plugin.basementZone().yBottom();
        int yTop = plugin.basementZone().yTop();
        int chunkBaseX = chunk.getX() << 4;
        int chunkBaseZ = chunk.getZ() << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int worldX = chunkBaseX + dx;
                int worldZ = chunkBaseZ + dz;
                if (!chamber.contains(chunk.getWorld(), worldX + 0.5, worldZ + 0.5)) {
                    continue;
                }
                for (int y = yBottom; y <= yTop; y++) {
                    Block block = chunk.getBlock(dx, y, dz);
                    if (block.getType() != Material.SPAWNER) {
                        continue;
                    }
                    BlockState state = block.getState();
                    if (state instanceof CreatureSpawner spawner) {
                        configureSpawner(spawner);
                        rememberSpawner(block.getLocation());
                    }
                }
            }
        }
    }

    private void rememberSpawner(Location loc) {
        Location key = loc.getBlock().getLocation();
        for (Location existing : spawnerBlocks) {
            if (sameBlock(existing, key)) {
                return;
            }
        }
        spawnerBlocks.add(key);
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static void configureSpawner(CreatureSpawner spawner) {
        List<SpawnerEntry> entries = new ArrayList<>();
        EntitySnapshot snapshot = spawner.getSpawnedEntity();
        EntityType type = spawner.getSpawnedType();
        if (snapshot == null && type != null && type != EntityType.UNKNOWN && type.isAlive()) {
            snapshot = createSnapshot(type);
        }
        if (snapshot != null) {
            entries.add(new SpawnerEntry(snapshot, 1, ANY_LIGHT));
        } else {
            for (SpawnerEntry entry : spawner.getPotentialSpawns()) {
                entry.setSpawnRule(ANY_LIGHT);
                entries.add(entry);
            }
        }
        if (entries.isEmpty()) {
            return;
        }
        spawner.setPotentialSpawns(entries);
        if (spawner instanceof Spawner paperSpawner) {
            paperSpawner.setMaxNearbyEntities(Math.max(paperSpawner.getMaxNearbyEntities(), 24));
            paperSpawner.setRequiredPlayerRange(Math.max(paperSpawner.getRequiredPlayerRange(), 16));
            paperSpawner.setMinSpawnDelay(100);
            paperSpawner.setMaxSpawnDelay(400);
            paperSpawner.resetTimer();
        }
        spawner.update(true, false);
    }

    private static EntitySnapshot createSnapshot(EntityType type) {
        try {
            return Bukkit.getServer().getEntityFactory().createEntitySnapshot(type.getKey().toString());
        } catch (IllegalArgumentException ex) {
            try {
                return Bukkit.getServer().getEntityFactory().createEntitySnapshot(type.getKey().getKey());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private static int floorDiv(int value, int divisor) {
        if (value >= 0) {
            return value / divisor;
        }
        return (value - divisor + 1) / divisor;
    }
}
