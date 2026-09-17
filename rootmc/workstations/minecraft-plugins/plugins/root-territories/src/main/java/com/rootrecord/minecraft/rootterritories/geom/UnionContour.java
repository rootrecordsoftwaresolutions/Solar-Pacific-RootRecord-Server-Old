package com.rootrecord.minecraft.rootterritories.geom;

import com.rootrecord.minecraft.rootterritories.model.NationTerritory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/** Outer outline of the union of all nation influence regions. */
public final class UnionContour {

    private UnionContour() {}

    /**
     * Merged map outline for {@code nation.contains(x,z)}.
     * Returns {@code double[points][2]} as x,z pairs in order.
     */
    public static double[][] compute(NationTerritory nation, int step, double mapInset) {
        return compute(nation, step, mapInset, (x, z) -> nation.contains(x, z, -mapInset));
    }

    public static double[][] compute(
            NationTerritory nation,
            int step,
            double mapInset,
            BiPredicate<Double, Double> containsAt) {
        if (nation.regions().isEmpty()) {
            return new double[0][0];
        }
        double[][] best = trace(nation, Math.max(8, step), mapInset, containsAt);
        if (best.length >= 3) {
            return best;
        }
        return trace(nation, Math.max(8, step / 2), mapInset, containsAt);
    }

    private static double[][] trace(
            NationTerritory nation,
            int step,
            double mapInset,
            BiPredicate<Double, Double> containsAt) {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (NationTerritory.InfluenceRegion region : nation.regions()) {
            double[] bounds = region.bufferedBounds();
            minX = Math.min(minX, bounds[0]);
            minZ = Math.min(minZ, bounds[1]);
            maxX = Math.max(maxX, bounds[2]);
            maxZ = Math.max(maxZ, bounds[3]);
        }

        int cols = Math.max(1, (int) Math.ceil((maxX - minX) / step));
        int rows = Math.max(1, (int) Math.ceil((maxZ - minZ) / step));

        boolean[][] corners = new boolean[cols + 1][rows + 1];
        for (int i = 0; i <= cols; i++) {
            double x = minX + i * step;
            for (int j = 0; j <= rows; j++) {
                double z = minZ + j * step;
                corners[i][j] = containsAt.test(x, z);
            }
        }

        List<double[]> segments = marchingSquareSegments(corners, minX, minZ, step);
        double[][] loop = longestClosedLoop(segments);
        if (loop.length < 3) {
            return loop;
        }
        return simplify(loop, step * 0.4);
    }

    private static List<double[]> marchingSquareSegments(
            boolean[][] corners,
            double minX,
            double minZ,
            int step) {
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

    private static double[][] longestClosedLoop(List<double[]> segments) {
        if (segments.isEmpty()) {
            return new double[0][0];
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
        double[][] best = new double[0][0];

        for (int start = 0; start < segments.size(); start++) {
            if (used[start]) {
                continue;
            }
            double[][] loop = walkLoop(start, segments, adjacency, used);
            if (loop.length > best.length) {
                best = loop;
            }
        }
        return best;
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
                return toArray(polygon);
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
        return new double[0][0];
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
        return toArray(out);
    }

    private static double[][] toArray(List<double[]> points) {
        return points.toArray(new double[0][]);
    }

    private static long key(double x, double z) {
        return (Math.round(x * 4.0) << 32) ^ (Math.round(z * 4.0) & 0xFFFFFFFFL);
    }
}
