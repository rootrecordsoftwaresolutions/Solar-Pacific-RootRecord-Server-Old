package com.rootrecord.minecraft.rootterritories.geom;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Horizontal spawn polygons from spawnarea-refined.txt (walls + map grief buffer). */
public final class SpawnExclusionZone {

    private final String worldName;
    private final List<int[]> vertices;

    private SpawnExclusionZone(String worldName, List<int[]> vertices) {
        this.worldName = worldName;
        this.vertices = vertices;
    }

    public static SpawnExclusionZone load(Plugin plugin) {
        try {
            var file = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_SPAWN_REFINED_FILE);
            seedDefaultIfNeeded(plugin, file);
            if (!file.isFile()) {
                return empty();
            }
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return parse(text);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not load spawn exclusion polygon: " + ex.getMessage());
            return empty();
        }
    }

    /** Grief buffer from plugins/RootMC/root-spawn.yml (matches Root-Spawn build protection). */
    public static int readGriefBufferBlocks(Plugin plugin) {
        var file = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_SPAWN_CONFIG);
        if (!file.isFile()) {
            return 20;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        return Math.max(0, cfg.getInt("protection.grief_buffer_blocks", 5));
    }

    private static void seedDefaultIfNeeded(Plugin plugin, java.io.File file) throws IOException {
        boolean seed = !file.isFile();
        if (!seed && file.isFile()) {
            SpawnExclusionZone current = parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            seed = current.isEmpty() || isLegacyOriginRing(current);
        }
        if (!seed) {
            return;
        }
        Plugin spawn = Bukkit.getPluginManager().getPlugin("Root-Spawn");
        if (spawn == null) {
            return;
        }
        try (InputStream in = spawn.getResource("spawnarea-refined.txt")) {
            if (in != null) {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Seeded spawnarea-refined.txt for map markers (ridge spawn walls).");
            }
        }
    }

    /** Pre–Jul 2026 circular ring near world origin. */
    private static boolean isLegacyOriginRing(SpawnExclusionZone zone) {
        if (zone.isEmpty()) {
            return false;
        }
        int maxX = Integer.MIN_VALUE;
        for (int[] v : zone.vertices()) {
            maxX = Math.max(maxX, v[0]);
        }
        return maxX > -500;
    }

    public static SpawnExclusionZone empty() {
        return new SpawnExclusionZone("world", List.of());
    }

    public boolean isEmpty() {
        return vertices.size() < 2;
    }

    public String worldName() {
        return worldName;
    }

    public List<int[]> vertices() {
        return vertices;
    }

    public boolean contains(String world, double x, double z) {
        if (world == null || !world.equals(worldName) || isEmpty()) {
            return false;
        }
        if (vertices.size() == 2) {
            return pointInAabb(x, z);
        }
        return pointInPolygon(x, z);
    }

    /** Inside walls or within grief buffer — matches Root-Spawn no build/break zone. */
    public boolean isNoBuildZone(String world, double x, double z, int griefBufferBlocks) {
        if (!contains(world, x, z)) {
            if (griefBufferBlocks <= 0) {
                return false;
            }
            if (vertices.size() == 2) {
                return pointInAabbPadded(x, z, griefBufferBlocks);
            }
            return distanceToEdge(x, z) <= griefBufferBlocks;
        }
        return true;
    }

    public double[][] mapPolygon() {
        return toMapCoords(vertices);
    }

    /** Outward grief buffer — matches Root-Spawn no build/break zone on BlueMap. */
    public double[][] mapGriefPolygon(int griefBufferBlocks) {
        if (isEmpty()) {
            return new double[0][0];
        }
        if (griefBufferBlocks <= 0) {
            return mapPolygon();
        }
        List<int[]> expanded = PolygonOffset.outward(vertices, griefBufferBlocks);
        return toMapCoords(expanded);
    }

    private static double[][] toMapCoords(List<int[]> points) {
        if (points.isEmpty()) {
            return new double[0][0];
        }
        double[][] out = new double[points.size()][2];
        for (int i = 0; i < points.size(); i++) {
            out[i][0] = points.get(i)[0] + 0.5;
            out[i][1] = points.get(i)[1] + 0.5;
        }
        return out;
    }

    private boolean pointInPolygon(double x, double z) {
        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = vertices.get(i)[0];
            double zi = vertices.get(i)[1];
            double xj = vertices.get(j)[0];
            double zj = vertices.get(j)[1];
            boolean intersect = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / (zj - zi + 1e-9) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private boolean pointInAabb(double x, double z) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] v : vertices) {
            minX = Math.min(minX, v[0]);
            maxX = Math.max(maxX, v[0]);
            minZ = Math.min(minZ, v[1]);
            maxZ = Math.max(maxZ, v[1]);
        }
        return x >= minX && x <= maxX + 1 && z >= minZ && z <= maxZ + 1;
    }

    private boolean pointInAabbPadded(double x, double z, int pad) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] v : vertices) {
            minX = Math.min(minX, v[0]);
            maxX = Math.max(maxX, v[0]);
            minZ = Math.min(minZ, v[1]);
            maxZ = Math.max(maxZ, v[1]);
        }
        return x >= minX - pad && x <= maxX + 1 + pad && z >= minZ - pad && z <= maxZ + 1 + pad;
    }

    private double distanceToEdge(double x, double z) {
        double min = Double.MAX_VALUE;
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            int[] a = vertices.get(i);
            int[] b = vertices.get((i + 1) % n);
            min = Math.min(min, distPointToSegment(x, z, a[0], a[1], b[0], b[1]));
        }
        return min;
    }

    private static double distPointToSegment(
            double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double apx = px - ax;
        double apz = pz - az;
        double ab2 = abx * abx + abz * abz;
        if (ab2 < 1e-9) {
            return Math.hypot(apx, apz);
        }
        double t = Math.max(0, Math.min(1, (apx * abx + apz * abz) / ab2));
        double closestX = ax + t * abx;
        double closestZ = az + t * abz;
        return Math.hypot(px - closestX, pz - closestZ);
    }

    static SpawnExclusionZone parse(String text) {
        String world = "world";
        List<int[]> pts = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("world=")) {
                world = line.substring("world=".length()).trim();
                continue;
            }
            if (line.startsWith("---") || line.startsWith("session_") || line.startsWith("updated_at")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 4) {
                continue;
            }
            try {
                world = parts[1].trim();
                int x = Integer.parseInt(parts[2].trim());
                int z = Integer.parseInt(parts[3].trim());
                pts.add(new int[] {x, z});
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return new SpawnExclusionZone(world, List.copyOf(pts));
    }
}
