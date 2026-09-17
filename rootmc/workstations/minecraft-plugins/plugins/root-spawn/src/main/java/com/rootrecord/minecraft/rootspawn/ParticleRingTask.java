package com.rootrecord.minecraft.rootspawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/** Surface-level ring particles, batched and player-scoped to reduce server load. */
public final class ParticleRingTask implements Runnable {

    private static final double VIEW_RADIUS = 96.0;
    private static final double VIEW_RADIUS_SQ = VIEW_RADIUS * VIEW_RADIUS;

    private final RootSpawnPlugin plugin;
    private BukkitTask task;
    /** x, z, surfaceY */
    private List<int[]> cachedSurface;
    private String cachedWorld;
    private int batchIndex;

    public ParticleRingTask(RootSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.config().particlesEnabled()) {
            return;
        }
        long period = plugin.config().particleIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        cachedSurface = null;
        batchIndex = 0;
    }

    public void invalidateCache() {
        cachedSurface = null;
        batchIndex = 0;
    }

    @Override
    public void run() {
        SpawnBoundary boundary = plugin.boundary();
        if (boundary == null || boundary.isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(boundary.worldName());
        if (world == null) {
            return;
        }
        if (cachedSurface == null || !boundary.worldName().equals(cachedWorld)) {
            rebuildSurface(world, boundary);
        }
        if (cachedSurface.isEmpty()) {
            return;
        }

        SpawnConfig cfg = plugin.config();
        int batch = Math.min(cfg.particleBatchSize(), cachedSurface.size());
        int end = Math.min(batchIndex + batch, cachedSurface.size());
        List<Player> viewers = world.getPlayers();
        if (viewers.isEmpty()) {
            batchIndex = end >= cachedSurface.size() ? 0 : end;
            return;
        }

        for (int i = batchIndex; i < end; i++) {
            int[] p = cachedSurface.get(i);
            Location base = new Location(world, p[0] + 0.5, p[2] + 0.08, p[1] + 0.5);
            spawnAt(viewers, base, cfg);
        }

        batchIndex = end >= cachedSurface.size() ? 0 : end;
    }

    private void rebuildSurface(World world, SpawnBoundary boundary) {
        int yOff = plugin.config().surfaceYOffset();
        List<int[]> edge = boundary.edgeGridPoints();
        cachedSurface = new ArrayList<>(edge.size());
        for (int[] p : edge) {
            int y = world.getHighestBlockYAt(p[0], p[1]) + yOff;
            cachedSurface.add(new int[] {p[0], p[1], y});
        }
        cachedWorld = boundary.worldName();
        batchIndex = 0;
    }

    private static void spawnAt(List<Player> viewers, Location base, SpawnConfig cfg) {
        if (cfg.particleStyle() == SpawnConfig.RingParticleStyle.GREEN_VILLAGER) {
            for (Player player : viewers) {
                if (player.getLocation().distanceSquared(base) > VIEW_RADIUS_SQ) {
                    continue;
                }
                for (int layer = 0; layer < cfg.particleLayers(); layer++) {
                    Location loc = base.clone().add(0, layer * 0.35, 0);
                    player.spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            loc,
                            cfg.particleCount(),
                            cfg.particleSpread(),
                            0.12,
                            cfg.particleSpread(),
                            0);
                }
            }
            return;
        }

        Particle.DustOptions dust = cfg.ringDust();
        double spread = cfg.particleSpread();
        for (Player player : viewers) {
            if (player.getLocation().distanceSquared(base) > VIEW_RADIUS_SQ) {
                continue;
            }
            for (int layer = 0; layer < cfg.particleLayers(); layer++) {
                Location loc = base.clone().add(0, layer * 0.32, 0);
                player.spawnParticle(
                        Particle.DUST,
                        loc,
                        cfg.particleCount(),
                        spread,
                        0.1,
                        spread,
                        0,
                        dust);
            }
        }
    }
}
