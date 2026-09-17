package com.rootrecord.minecraft.rootclaims;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Outer perimeter(s) of the union of circular claims for one owner. */
public final class ClaimUnionGeometry {

    private ClaimUnionGeometry() {}

    public static boolean covers(List<ClaimRecord> claims, double x, double z) {
        return covers(claims, x, z, 0);
    }

    public static boolean covers(List<ClaimRecord> claims, double x, double z, int radiusExtra) {
        int extra = Math.max(0, radiusExtra);
        for (ClaimRecord claim : claims) {
            double dx = x - claim.key().x();
            double dz = z - claim.key().z();
            double r = claim.radiusBlocks() + extra;
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closed outer loops of the union as {@code double[points][2]} x/z rings.
     * Disjoint claim clusters yield multiple loops.
     */
    public static List<double[][]> outerLoops(List<ClaimRecord> claims, int step) {
        return outerLoops(claims, step, 0);
    }

    /**
     * Same as {@link #outerLoops(List, int)} but each claim radius is expanded by
     * {@code radiusExtra} (used for BlueMap territory rings).
     */
    public static List<double[][]> outerLoops(List<ClaimRecord> claims, int step, int radiusExtra) {
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        int extra = Math.max(0, radiusExtra);
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (ClaimRecord claim : claims) {
            double r = claim.radiusBlocks() + extra + 2;
            minX = Math.min(minX, claim.key().x() - r);
            minZ = Math.min(minZ, claim.key().z() - r);
            maxX = Math.max(maxX, claim.key().x() + r);
            maxZ = Math.max(maxZ, claim.key().z() + r);
        }
        int grid = Math.max(2, step);
        List<double[][]> loops = traceAll(claims, minX, minZ, maxX, maxZ, grid, extra);
        if (!loops.isEmpty()) {
            return loops;
        }
        return traceAll(claims, minX, minZ, maxX, maxZ, Math.max(2, grid / 2), extra);
    }

    /** Sample points along outer loops at roughly {@code spacing} block intervals. */
    public static List<double[]> samplePerimeter(List<ClaimRecord> claims, int step, double spacing) {
        List<double[]> out = new ArrayList<>();
        double gap = Math.max(0.75, spacing);
        for (double[][] loop : outerLoops(claims, step)) {
            if (loop.length < 3) {
                continue;
            }
            double carry = 0;
            for (int i = 0; i < loop.length; i++) {
                double[] a = loop[i];
                double[] b = loop[(i + 1) % loop.length];
                double dx = b[0] - a[0];
                double dz = b[1] - a[1];
                double len = Math.hypot(dx, dz);
                if (len < 1e-6) {
                    continue;
                }
                double t = carry;
                while (t <= len) {
                    double u = t / len;
                    out.add(new double[] {a[0] + dx * u, a[1] + dz * u});
                    t += gap;
                }
                carry = t - len;
            }
        }
        return out;
    }

    public static Map<UUID, List<ClaimRecord>> groupByOwner(Iterable<ClaimRecord> claims) {
        Map<UUID, List<ClaimRecord>> byOwner = new LinkedHashMap<>();
        for (ClaimRecord claim : claims) {
            byOwner.computeIfAbsent(claim.ownerId(), ignored -> new ArrayList<>()).add(claim);
        }
        return byOwner;
    }

    private static List<double[][]> traceAll(
            List<ClaimRecord> claims,
            double minX,
            double minZ,
            double maxX,
            double maxZ,
            int step,
            int radiusExtra) {
        int cols = Math.max(1, (int) Math.ceil((maxX - minX) / step));
        int rows = Math.max(1, (int) Math.ceil((maxZ - minZ) / step));
        boolean[][] corners = new boolean[cols + 1][rows + 1];
        for (int i = 0; i <= cols; i++) {
            double x = minX + i * step;
            for (int j = 0; j <= rows; j++) {
                double z = minZ + j * step;
                corners[i][j] = covers(claims, x, z, radiusExtra);
            }
        }
        List<double[]> segments = marchingSquareSegments(corners, minX, minZ, step);
        List<double[][]> loops = allClosedLoops(segments);
        List<double[][]> simplified = new ArrayList<>();
        double minDistance = step * 0.35;
        for (double[][] loop : loops) {
            if (loop.length >= 3) {
                simplified.add(simplify(loop, minDistance));
            }
        }
        return simplified;
    }

    private static List<double[]> marchingSquareSegments(
            boolean[][] corners, double minX, double minZ, int step) {
        List<double[]> segments = new ArrayList<>();
        int cols = corners.length - 1;
        int rows = corners[0].length - 1;
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                int index = 0;
                if (corners[i][j]) index |= 1;
                if (corners[i + 1][j]) index |= 2;
                if (corners[i + 1][j + 1]) index |= 4;
                if (corners[i][j + 1]) index |= 8;
                double x = minX + i * step;
                double z = minZ + j * step;
                double midX = x + step * 0.5;
                double midZ = z + step * 0.5;
                double east = x + step;
                double north = z + step;
                switch (index) {
                    case 0, 15 -> { }
                    case 1 -> addSegment(segments, x, midZ, midX, z);
                    case 2 -> addSegment(segments, midX, z, east, midZ);
                    case 3 -> addSegment(segments, x, midZ, east, midZ);
                    case 4 -> addSegment(segments, east, midZ, midX, north);
                    case 5 -> {
                        addSegment(segments, x, midZ, midX, z);
                        addSegment(segments, east, midZ, midX, north);
                    }
                    case 6 -> addSegment(segments, midX, z, midX, north);
                    case 7 -> addSegment(segments, x, midZ, midX, north);
                    case 8 -> addSegment(segments, midX, north, x, midZ);
                    case 9 -> addSegment(segments, midX, north, midX, z);
                    case 10 -> {
                        addSegment(segments, midX, z, east, midZ);
                        addSegment(segments, midX, north, x, midZ);
                    }
                    case 11 -> addSegment(segments, midX, north, east, midZ);
                    case 12 -> addSegment(segments, east, midZ, x, midZ);
                    case 13 -> addSegment(segments, midX, z, east, midZ);
                    case 14 -> addSegment(segments, midX, z, x, midZ);
                    default -> { }
                }
            }
        }
        return segments;
    }

    private static void addSegment(List<double[]> segments, double x1, double z1, double x2, double z2) {
        segments.add(new double[] {x1, z1, x2, z2});
    }

    private static List<double[][]> allClosedLoops(List<double[]> segments) {
        if (segments.isEmpty()) {
            return List.of();
        }
        Map<Long, List<int[]>> adjacency = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            double[] segment = segments.get(i);
            long a = key(segment[0], segment[1]);
            long b = key(segment[2], segment[3]);
            adjacency.computeIfAbsent(a, ignored -> new ArrayList<>()).add(new int[] {i, 0});
            adjacency.computeIfAbsent(b, ignored -> new ArrayList<>()).add(new int[] {i, 1});
        }
        boolean[] used = new boolean[segments.size()];
        List<double[][]> loops = new ArrayList<>();
        for (int start = 0; start < segments.size(); start++) {
            if (used[start]) {
                continue;
            }
            double[][] loop = walkLoop(start, segments, adjacency, used);
            if (loop.length >= 3) {
                loops.add(loop);
            }
        }
        return loops;
    }

    private static double[][] walkLoop(
            int startSegment,
            List<double[]> segments,
            Map<Long, List<int[]>> adjacency,
            boolean[] used) {
        double[] first = segments.get(startSegment);
        used[startSegment] = true;
        long startKey = key(first[0], first[1]);
        long current = key(first[2], first[3]);
        List<double[]> polygon = new ArrayList<>();
        polygon.add(new double[] {first[0], first[1]});
        int guard = 0;
        while (guard++ < segments.size() * 2) {
            if (current == startKey) {
                return polygon.toArray(new double[0][]);
            }
            int[] nextEdge = pickNextEdge(current, adjacency, used);
            if (nextEdge == null) {
                break;
            }
            double[] segment = segments.get(nextEdge[0]);
            used[nextEdge[0]] = true;
            long endA = key(segment[0], segment[1]);
            long endB = key(segment[2], segment[3]);
            long next = current == endA ? endB : endA;
            double nx = next == endB ? segment[2] : segment[0];
            double nz = next == endB ? segment[3] : segment[1];
            polygon.add(new double[] {nx, nz});
            current = next;
        }
        return new double[0][];
    }

    private static int[] pickNextEdge(long node, Map<Long, List<int[]>> adjacency, boolean[] used) {
        List<int[]> edges = adjacency.get(node);
        if (edges == null) {
            return null;
        }
        for (int[] edge : edges) {
            if (!used[edge[0]]) {
                return edge;
            }
        }
        return null;
    }

    private static double[][] simplify(double[][] points, double minDistance) {
        if (points.length <= 3) {
            return points;
        }
        List<double[]> out = new ArrayList<>();
        double[] last = points[0];
        out.add(last);
        for (int i = 1; i < points.length; i++) {
            double[] point = points[i];
            if (Math.hypot(point[0] - last[0], point[1] - last[1]) >= minDistance) {
                out.add(point);
                last = point;
            }
        }
        return out.toArray(new double[0][]);
    }

    private static long key(double x, double z) {
        return (Math.round(x * 4.0) << 32) ^ (Math.round(z * 4.0) & 0xFFFFFFFFL);
    }
}
