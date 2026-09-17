package com.rootrecord.minecraft.rootterritories.particle;

import com.rootrecord.minecraft.rootterritories.config.TerritoriesConfig;
import com.rootrecord.minecraft.rootterritories.geom.RingOutline;
import com.rootrecord.minecraft.rootterritories.geom.SpawnExclusionZone;
import com.rootrecord.minecraft.rootterritories.model.NationTerritory;
import com.rootrecord.minecraft.rootterritories.model.TownTerritory;
import com.rootrecord.minecraft.rootterritories.service.TerritoryService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Surface-level ring particles along exact nation (red) and town (blue) influence borders. */
public final class TerritoryParticleTask implements Runnable {

    private final JavaPlugin plugin;
    private final TerritoryService territories;
    private BukkitTask task;
    private Map<String, WorldRings> ringsByWorld = Map.of();
    private int batchIndex;

    public TerritoryParticleTask(JavaPlugin plugin, TerritoryService territories) {
        this.plugin = plugin;
        this.territories = territories;
    }

    public void start() {
        stop();
        TerritoriesConfig cfg = territories.config();
        if (!cfg.enabled() || !cfg.particlesEnabled()) {
            return;
        }
        rebuildRings();
        long period = cfg.particleIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        ringsByWorld = Map.of();
        batchIndex = 0;
    }

    public void rebuildRings() {
        TerritoriesConfig cfg = territories.config();
        if (!cfg.enabled() || !cfg.particlesEnabled()) {
            ringsByWorld = Map.of();
            batchIndex = 0;
            return;
        }

        Particle.DustOptions nationDust = dust(cfg.nationParticleRgb(), cfg.particleSize());
        Particle.DustOptions townDust = dust(cfg.townParticleRgb(), cfg.particleSize());
        int edgeSpacing = cfg.particleEdgeSpacing();
        int griefBuffer = SpawnExclusionZone.readGriefBufferBlocks(plugin);
        var spawn = territories.spawnZone();
        Map<String, WorldRings> built = new HashMap<>();

        for (NationTerritory nation : territories.nations()) {
            appendOutline(
                    built,
                    nation.world(),
                    territories.exclusiveParticleNationPolygon(nation),
                    nationDust,
                    edgeSpacing,
                    spawn,
                    griefBuffer);
        }
        for (TownTerritory town : territories.independentTowns()) {
            appendOutline(
                    built,
                    town.world(),
                    territories.exclusiveParticleTownPolygon(town),
                    townDust,
                    edgeSpacing,
                    spawn,
                    griefBuffer);
        }

        ringsByWorld = Map.copyOf(built);
        batchIndex = 0;
    }

    private static void appendOutline(
            Map<String, WorldRings> out,
            String world,
            double[][] polygon,
            Particle.DustOptions dust,
            int edgeSpacing,
            SpawnExclusionZone spawn,
            int griefBuffer) {
        List<int[]> edge = RingOutline.edgeGrid(polygon);
        if (edge.isEmpty()) {
            return;
        }
        WorldRings rings = out.computeIfAbsent(world, ignored -> new WorldRings(world));
        int spacing = Math.max(1, edgeSpacing);
        for (int i = 0; i < edge.size(); i += spacing) {
            int[] xz = edge.get(i);
            if (spawn != null && spawn.isNoBuildZone(world, xz[0] + 0.5, xz[1] + 0.5, griefBuffer)) {
                continue;
            }
            rings.add(xz[0], xz[1], dust);
        }
    }

    @Override
    public void run() {
        if (ringsByWorld.isEmpty()) {
            return;
        }
        TerritoriesConfig cfg = territories.config();
        if (!cfg.enabled() || !cfg.particlesEnabled()) {
            return;
        }

        double viewRadiusSq = cfg.particleViewRadiusSq();
        int chunkRadius = (int) Math.ceil(cfg.particleViewRadius() / 16.0) + 1;
        int batch = cfg.particleBatchSize();
        List<BorderPoint> nearby = new ArrayList<>();

        for (Map.Entry<String, WorldRings> entry : ringsByWorld.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            List<Player> viewers = world.getPlayers();
            if (viewers.isEmpty()) {
                continue;
            }

            Set<Long> seen = new HashSet<>();
            for (Player player : viewers) {
                Location loc = player.getLocation();
                int centerChunkX = loc.getBlockX() >> 4;
                int centerChunkZ = loc.getBlockZ() >> 4;
                for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                    for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                        List<BorderPoint> chunkPoints = entry.getValue().byChunk.get(chunkKey(centerChunkX + dx, centerChunkZ + dz));
                        if (chunkPoints == null) {
                            continue;
                        }
                        for (BorderPoint point : chunkPoints) {
                            if (!seen.add(pointKey(point.x, point.z))) {
                                continue;
                            }
                            double distSq = horizontalDistanceSq(loc, point.x, point.z);
                            if (distSq <= viewRadiusSq) {
                                nearby.add(point);
                            }
                        }
                    }
                }
            }
        }

        if (nearby.isEmpty()) {
            return;
        }

        int slice = Math.min(batch, nearby.size());
        int start = batchIndex % nearby.size();
        for (int i = 0; i < slice; i++) {
            BorderPoint point = nearby.get((start + i) % nearby.size());
            World world = Bukkit.getWorld(point.world);
            if (world == null) {
                continue;
            }
            List<Player> viewers = world.getPlayers();
            if (viewers.isEmpty()) {
                continue;
            }
            int y = point.surfaceY(world, cfg.particleSurfaceYOffset());
            Location base = new Location(world, point.x + 0.5, y + 0.08, point.z + 0.5);
            spawnAt(viewers, base, cfg, point.dust);
        }

        batchIndex = (start + slice) % nearby.size();
    }

    private static double horizontalDistanceSq(Location loc, int x, int z) {
        double dx = loc.getX() - (x + 0.5);
        double dz = loc.getZ() - (z + 0.5);
        return dx * dx + dz * dz;
    }

    private static void spawnAt(
            List<Player> viewers,
            Location base,
            TerritoriesConfig cfg,
            Particle.DustOptions dust) {
        double radiusSq = cfg.particleViewRadiusSq();
        double spread = cfg.particleSpread();
        for (Player player : viewers) {
            if (player.getLocation().distanceSquared(base) > radiusSq) {
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

    private static Particle.DustOptions dust(int[] rgb, float size) {
        int r = rgb.length > 0 ? rgb[0] : 255;
        int g = rgb.length > 1 ? rgb[1] : 255;
        int b = rgb.length > 2 ? rgb[2] : 255;
        return new Particle.DustOptions(Color.fromRGB(r, g, b), size);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static long pointKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static final class WorldRings {
        private final String world;
        private final Map<Long, List<BorderPoint>> byChunk = new HashMap<>();

        private WorldRings(String world) {
            this.world = world;
        }

        private void add(int x, int z, Particle.DustOptions dust) {
            BorderPoint point = new BorderPoint(world, x, z, dust);
            byChunk.computeIfAbsent(chunkKey(x >> 4, z >> 4), ignored -> new ArrayList<>()).add(point);
        }
    }

    private static final class BorderPoint {
        private final String world;
        private final int x;
        private final int z;
        private final Particle.DustOptions dust;
        private int cachedY = Integer.MIN_VALUE;

        private BorderPoint(String world, int x, int z, Particle.DustOptions dust) {
            this.world = world;
            this.x = x;
            this.z = z;
            this.dust = dust;
        }

        private int surfaceY(World world, int yOffset) {
            if (cachedY == Integer.MIN_VALUE) {
                cachedY = world.getHighestBlockYAt(x, z) + yOffset;
            }
            return cachedY;
        }
    }
}
