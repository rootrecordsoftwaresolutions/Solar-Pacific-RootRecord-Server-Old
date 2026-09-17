package com.rootrecord.minecraft.rootterritories.geom;

import java.util.ArrayList;
import java.util.List;

/** Rasterize a closed x,z polygon into block-edge grid points for particle rings. */
public final class RingOutline {

    private RingOutline() {}

    public static List<int[]> edgeGrid(double[][] polygon) {
        if (polygon == null || polygon.length < 2) {
            return List.of();
        }
        List<int[]> out = new ArrayList<>();
        int n = polygon.length;
        for (int i = 0; i < n; i++) {
            int[] a = block(polygon[i]);
            int[] b = block(polygon[(i + 1) % n]);
            out.addAll(bresenham(a[0], a[1], b[0], b[1], true));
        }
        return out;
    }

    private static int[] block(double[] point) {
        return new int[] {(int) Math.floor(point[0]), (int) Math.floor(point[1])};
    }

    private static List<int[]> bresenham(int x0, int z0, int x1, int z1, boolean skipLast) {
        List<int[]> points = new ArrayList<>();
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int x = x0;
        int z = z0;
        while (true) {
            points.add(new int[] {x, z});
            if (x == x1 && z == z1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
        if (skipLast && points.size() > 1) {
            points.remove(points.size() - 1);
        }
        return points;
    }
}
