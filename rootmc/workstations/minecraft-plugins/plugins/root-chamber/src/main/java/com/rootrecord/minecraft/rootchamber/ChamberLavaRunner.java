package com.rootrecord.minecraft.rootchamber;

import com.rootrecord.minecraft.rootspawn.LavaSpot;
import com.rootrecord.minecraft.rootspawn.RootSpawnPlugin;
import com.rootrecord.minecraft.rootspawn.SpawnConfig;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Randomly opens mapped lava columns during a chamber run; restores all blocks at end. */
final class ChamberLavaRunner {

    private final RootChamberPlugin plugin;
    private final RootSpawnPlugin spawn;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<BlockSnapshot> snapshots = new ArrayList<>();
    private final AtomicInteger spotsOpened = new AtomicInteger();
    private int totalSpots;

    ChamberLavaRunner(RootChamberPlugin plugin, RootSpawnPlugin spawn) {
        this.plugin = plugin;
        this.spawn = spawn;
    }

    int totalSpots() { return totalSpots; }
    int ceilingsOpened() { return spotsOpened.get(); }
    int floorsOpened() { return spotsOpened.get(); }
    int spotsRemaining() { return Math.max(0, totalSpots - spotsOpened.get()); }

    void start(UUID playerId, int durationSeconds, List<LavaSpot> spots) {
        cancelPending();
        snapshots.clear();
        spotsOpened.set(0);
        totalSpots = spots == null ? 0 : spots.size();
        if (spots == null || spots.isEmpty()) {
            return;
        }

        for (LavaSpot spot : spots) {
            World world = Bukkit.getWorld(spot.world());
            if (world == null) {
                continue;
            }
            snapshotColumn(world, spot);
        }

        long durationTicks = Math.max(40L, durationSeconds * 20L);
        long minTick = 40L;
        long maxTick = Math.max(minTick + 1, durationTicks - 40L);
        Random random = new Random();
        List<Long> openDelays = new ArrayList<>(spots.size());
        for (int i = 0; i < spots.size(); i++) {
            openDelays.add(minTick + (long) (random.nextDouble() * (maxTick - minTick)));
        }
        Collections.sort(openDelays);

        for (int i = 0; i < spots.size(); i++) {
            LavaSpot spot = spots.get(i);
            World world = Bukkit.getWorld(spot.world());
            if (world == null) {
                continue;
            }
            int spotIndex = i + 1;
            long delay = openDelays.get(i);
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!spawn.isChamberMinigameRunning(playerId)) {
                    return;
                }
                int broken = openColumn(world, spot);
                spotsOpened.incrementAndGet();
                notifySpotOpened(playerId, spotIndex, broken);
                plugin.getLogger().fine("Chamber lava spot #" + spotIndex + " opened (" + broken + " blocks)");
            }, delay));
        }
    }

    void cancelPending() {
        for (BukkitTask task : tasks) {
            if (task != null) {
                task.cancel();
            }
        }
        tasks.clear();
    }

    void restoreAll() {
        cancelPending();
        for (BlockSnapshot snap : snapshots) {
            snap.restore();
        }
        snapshots.clear();
        spotsOpened.set(0);
        totalSpots = 0;
    }

    private void notifySpotOpened(UUID playerId, int spotIndex, int blocksBroken) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        SpawnConfig cfg = spawn.config();
        spawn.chamberMsg(player, cfg.chamberLavaSpotOpen()
                .replace("{n}", Integer.toString(spotIndex))
                .replace("{total}", Integer.toString(totalSpots))
                .replace("{blocks}", Integer.toString(blocksBroken)));
        spawn.showChamberTitle(
                player,
                cfg.chamberLavaBreachTitle(),
                cfg.chamberLavaSpotOpenSubtitle()
                        .replace("{n}", Integer.toString(spotIndex))
                        .replace("{total}", Integer.toString(totalSpots)),
                5, 50, 15);
        player.playSound(player.getLocation(), resolveSound(cfg.chamberLavaBreachSound()), 1.0f, 0.85f);
        player.playSound(player.getLocation(), resolveSound(cfg.chamberLavaFloorSound()), 0.8f, 1.0f);
    }

    private static Sound resolveSound(String name) {
        if (name == null || name.isBlank()) {
            return Sound.ENTITY_GHAST_SCREAM;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Sound.ENTITY_GHAST_SCREAM;
        }
    }

    private void snapshotColumn(World world, LavaSpot spot) {
        ColumnBounds bounds = columnBounds(spot);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    snapshot(world, x, y, z);
                }
            }
        }
    }

    private int openColumn(World world, LavaSpot spot) {
        ColumnBounds bounds = columnBounds(spot);
        ensureChunkLoaded(world, bounds.minX(), bounds.minZ());
        if (bounds.minX() >> 4 != bounds.maxX() >> 4 || bounds.minZ() >> 4 != bounds.maxZ() >> 4) {
            ensureChunkLoaded(world, bounds.maxX(), bounds.maxZ());
        }
        int broken = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
                    if (breakBlock(world, x, y, z)) {
                        broken++;
                    }
                }
            }
        }
        return broken;
    }

    private static ColumnBounds columnBounds(LavaSpot spot) {
        return new ColumnBounds(
                Math.min(spot.ceilingX(), spot.floorX()),
                Math.max(spot.ceilingX(), spot.floorX()),
                Math.min(spot.ceilingY(), spot.floorY()),
                Math.max(spot.ceilingY(), spot.floorY()),
                Math.min(spot.ceilingZ(), spot.floorZ()),
                Math.max(spot.ceilingZ(), spot.floorZ()));
    }

    private record ColumnBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    private static void ensureChunkLoaded(World world, int x, int z) {
        Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
        if (!chunk.isLoaded()) {
            chunk.load();
        }
    }

    private void snapshot(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType().isAir()) {
            return;
        }
        for (BlockSnapshot existing : snapshots) {
            if (existing.matches(world, x, y, z)) {
                return;
            }
        }
        snapshots.add(new BlockSnapshot(world, x, y, z, block.getType(), block.getBlockData()));
    }

    private static boolean breakBlock(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType().isAir()) {
            return false;
        }
        block.setType(Material.AIR, true);
        return true;
    }

    private static final class BlockSnapshot {
        private final World world;
        private final int x;
        private final int y;
        private final int z;
        private final Material type;
        private final BlockData data;

        BlockSnapshot(World world, int x, int y, int z, Material type, BlockData data) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.data = data;
        }

        boolean matches(World otherWorld, int ox, int oy, int oz) {
            return world.equals(otherWorld) && x == ox && y == oy && z == oz;
        }

        void restore() {
            ensureChunkLoaded(world, x, z);
            Block block = world.getBlockAt(x, y, z);
            block.setType(type, false);
            if (data != null) {
                block.setBlockData(data, false);
            }
        }
    }
}
