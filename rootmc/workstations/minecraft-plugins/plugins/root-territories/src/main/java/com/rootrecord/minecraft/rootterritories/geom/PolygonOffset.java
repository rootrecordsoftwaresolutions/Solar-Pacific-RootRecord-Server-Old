package com.rootrecord.minecraft.rootterritories.geom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Outward grief buffer contour for horizontal (x,z) polygons — matches Root-Spawn distance checks. */
public final class PolygonOffset {

    private PolygonOffset() {}

    /**
     * Closed contour around {@code inside polygon} ∪ {@code distance-to-edge ≤ distance}.
     * Grid-sampled at block centers so BlueMap fill matches in-game build protection.
     */
    public static List<int[]> outward(List<int[]> vertices, double distance) {
        int n = vertices.size();
        if (n < 3 || distance <= 0) {
            return List.copyOf(vertices);
        }
        int buffer = (int) Math.ceil(distance);
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
        int pad = buffer + 2;
        minX -= pad;
        maxX += pad;
        minZ -= pad;
        maxZ += pad;

        Set<Long> protectedCells = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = x + 0.5;
                double cz = z + 0.5;
                if (pointInPolygon(vertices, cx, cz) || distanceToEdge(vertices, cx, cz) <= distance) {
                    protectedCells.add(cellKey(x, z));
                }
            }
        }
        if (protectedCells.isEmpty()) {
            return List.copyOf(vertices);
        }
        return traceCellUnionBoundary(protectedCells);
    }

    private static List<int[]> traceCellUnionBoundary(Set<Long> protectedCells) {
        Map<Long, List<int[]>> adj = new HashMap<>();
        for (long key : protectedCells) {
            int x = cellX(key);
            int z = cellZ(key);
            if (!protectedCells.contains(cellKey(x, z - 1))) {
                addDirectedEdge(adj, x, z, x + 1, z);
            }
            if (!protectedCells.contains(cellKey(x, z + 1))) {
                addDirectedEdge(adj, x + 1, z + 1, x, z + 1);
            }
            if (!protectedCells.contains(cellKey(x - 1, z))) {
                addDirectedEdge(adj, x, z + 1, x, z);
            }
            if (!protectedCells.contains(cellKey(x + 1, z))) {
                addDirectedEdge(adj, x + 1, z, x + 1, z + 1);
            }
        }
        if (adj.isEmpty()) {
            return List.of();
        }

        int[] start = null;
        for (long pointKey : adj.keySet()) {
            int x = pointX(pointKey);
            int z = pointZ(pointKey);
            if (start == null || x < start[0] || (x == start[0] && z < start[1])) {
                start = new int[] {x, z};
            }
        }
        if (start == null) {
            return List.of();
        }

        List<int[]> contour = new ArrayList<>();
        contour.add(start);
        int[] prev = null;
        int[] cur = start;
        int guard = adj.size() + 10;
        while (guard-- > 0) {
            List<int[]> neighbors = adj.get(pointKey(cur[0], cur[1]));
            if (neighbors == null || neighbors.isEmpty()) {
                break;
            }
            int[] next = null;
            for (int[] n : neighbors) {
                if (prev == null || n[0] != prev[0] || n[1] != prev[1]) {
                    next = n;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            if (next[0] == start[0] && next[1] == start[1]) {
                break;
            }
            contour.add(next);
            prev = cur;
            cur = next;
        }
        return contour;
    }

    private static void addDirectedEdge(Map<Long, List<int[]>> adj, int x1, int z1, int x2, int z2) {
        adj.computeIfAbsent(pointKey(x1, z1), ignored -> new ArrayList<>()).add(new int[] {x2, z2});
    }

    private static boolean pointInPolygon(List<int[]> vertices, double x, double z) {
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

    private static double distanceToEdge(List<int[]> vertices, double x, double z) {
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

    private static long cellKey(int x, int z) {
        return pointKey(x, z);
    }

    private static long pointKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static int cellX(long key) {
        return (int) (key >> 32);
    }

    private static int cellZ(long key) {
        return (int) key;
    }

    private static int pointX(long key) {
        return cellX(key);
    }

    private static int pointZ(long key) {
        return cellZ(key);
    }
}
