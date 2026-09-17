package com.rootrecord.minecraft.rootmapper;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/** Closed polygon boundary on the XZ plane (y ignored). */
public final class PolygonBoundary {

    private final String worldName;
    private final List<int[]> vertices;

    public PolygonBoundary(String worldName, List<int[]> vertices) {
        this.worldName = worldName;
        this.vertices = new ArrayList<>();
        for (int[] v : vertices) {
            this.vertices.add(new int[] {v[0], v[1]});
        }
    }

    public String worldName() {
        return worldName;
    }

    public List<int[]> vertices() {
        return vertices;
    }

    public boolean isEmpty() {
        return vertices.size() < 2;
    }

    public int pointCount() {
        return vertices.size();
    }

    public boolean contains(World world, double x, double z) {
        if (world == null || !world.getName().equals(worldName) || isEmpty()) {
            return false;
        }
        if (vertices.size() == 2) {
            return pointInAabb(x, z);
        }
        return pointInPolygon(x, z);
    }

    public boolean contains(Location loc) {
        return loc != null && contains(loc.getWorld(), loc.getX(), loc.getZ());
    }

    public int pullInward(int clickX, int clickZ) {
        if (isEmpty() || !pointInPolygon(clickX + 0.5, clickZ + 0.5)) {
            return 0;
        }
        return adjustRadial(clickX, clickZ, true);
    }

    public int pushOutward(int clickX, int clickZ) {
        if (isEmpty() || pointInPolygon(clickX + 0.5, clickZ + 0.5)) {
            return 0;
        }
        return adjustRadial(clickX, clickZ, false);
    }

    public boolean isNearOutside(World world, double x, double z, int maxDist) {
        if (world == null || !world.getName().equals(worldName) || isEmpty()) {
            return false;
        }
        if (pointInPolygon(x, z)) {
            return false;
        }
        return distanceToEdge(x, z) <= maxDist;
    }

    public double distanceToEdge(double x, double z) {
        double min = Double.MAX_VALUE;
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            int[] a = vertices.get(i);
            int[] b = vertices.get((i + 1) % n);
            min = Math.min(min, distPointToSegment(x, z, a[0], a[1], b[0], b[1]));
        }
        return min;
    }

    public PolygonBoundary copy() {
        List<int[]> copy = new ArrayList<>();
        for (int[] v : vertices) {
            copy.add(new int[] {v[0], v[1]});
        }
        return new PolygonBoundary(worldName, copy);
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

    private int adjustRadial(int clickX, int clickZ, boolean inward) {
        double[] c = centroidXZ();
        double cx = c[0];
        double cz = c[1];
        double clickAng = Math.atan2(clickZ - cz, clickX - cx);
        double clickR = Math.hypot(clickX - cx, clickZ - cz);
        int best = nearestVertexIndex(clickAng, cx, cz);

        int moved = 0;
        int n = vertices.size();
        for (int di = -3; di <= 3; di++) {
            int idx = Math.floorMod(best + di, n);
            int[] v = vertices.get(idx);
            double ang = Math.atan2(v[1] - cz, v[0] - cx);
            double r = Math.hypot(v[0] - cx, v[1] - cz);
            double weight = di == 0 ? 1.0 : Math.max(0.25, 1.0 - Math.abs(di) * 0.2);
            double targetR;
            if (inward) {
                targetR = Math.min(r, clickR + 2.0 + weight);
                if (targetR >= r - 0.5) {
                    continue;
                }
            } else {
                targetR = Math.max(r, Math.min(clickR, r + 3.0 + weight));
                if (targetR <= r + 0.5) {
                    continue;
                }
            }
            int nx = (int) Math.round(cx + targetR * Math.cos(ang));
            int nz = (int) Math.round(cz + targetR * Math.sin(ang));
            if (nx != v[0] || nz != v[1]) {
                v[0] = nx;
                v[1] = nz;
                moved++;
            }
        }
        return moved;
    }

    private double[] centroidXZ() {
        double cx = 0;
        double cz = 0;
        for (int[] v : vertices) {
            cx += v[0];
            cz += v[1];
        }
        int n = vertices.size();
        return new double[] {cx / n, cz / n};
    }

    private int nearestVertexIndex(double clickAng, double cx, double cz) {
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < vertices.size(); i++) {
            int[] v = vertices.get(i);
            double ang = Math.atan2(v[1] - cz, v[0] - cx);
            double diff = Math.abs(normalizeAngle(ang - clickAng));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
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

    private static double normalizeAngle(double rad) {
        while (rad > Math.PI) {
            rad -= 2 * Math.PI;
        }
        while (rad < -Math.PI) {
            rad += 2 * Math.PI;
        }
        return rad;
    }

    public static PolygonBoundary parse(String text) {
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
        return new PolygonBoundary(world, pts);
    }

    private static double distPointToSegment(
            double px, double pz, int ax, int az, int bx, int bz) {
        return distPointToSegment(px, pz, (double) ax, (double) az, (double) bx, (double) bz);
    }
}
