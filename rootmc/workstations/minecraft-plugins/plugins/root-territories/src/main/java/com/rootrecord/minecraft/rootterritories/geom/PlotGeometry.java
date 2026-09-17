package com.rootrecord.minecraft.rootterritories.geom;

import com.rootrecord.minecraft.rootterritories.model.PlotRect;

import java.util.List;

public final class PlotGeometry {

    private PlotGeometry() {}

    /** Shortest distance from (x,z) to the union of plot rectangles (0 when inside any plot). */
    public static double distanceToPlots(double x, double z, List<PlotRect> plots) {
        double best = Double.MAX_VALUE;
        for (PlotRect plot : plots) {
            best = Math.min(best, distanceToRect(x, z, plot));
        }
        return best;
    }

    public static boolean withinBuffer(double x, double z, List<PlotRect> plots, double buffer) {
        return distanceToPlots(x, z, plots) <= buffer;
    }

    public static boolean insidePlots(double x, double z, List<PlotRect> plots) {
        return distanceToPlots(x, z, plots) <= 0.01;
    }

    /** Axis-aligned bounds of all plots expanded outward by {@code buffer} (block coords). */
    public static double[] bufferedBounds(List<PlotRect> plots, double buffer) {
        if (plots.isEmpty()) {
            return new double[] {0, 0, 0, 0};
        }
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (PlotRect plot : plots) {
            minX = Math.min(minX, plot.minX());
            minZ = Math.min(minZ, plot.minZ());
            maxX = Math.max(maxX, plot.maxX());
            maxZ = Math.max(maxZ, plot.maxZ());
        }
        return new double[] {
                minX - buffer,
                minZ - buffer,
                maxX + buffer,
                maxZ + buffer
        };
    }

    /** Circle matching in-game buffer from plot edges: {centerX, centerZ, radius}. */
    public static double[] influenceCircle(List<PlotRect> plots, double buffer) {
        if (plots.isEmpty()) {
            return new double[] {0, 0, 0};
        }
        double sumX = 0;
        double sumZ = 0;
        for (PlotRect plot : plots) {
            sumX += plot.centerX();
            sumZ += plot.centerZ();
        }
        double cx = sumX / plots.size();
        double cz = sumZ / plots.size();
        double radius = buffer;
        for (PlotRect plot : plots) {
            int[][] corners = {
                    {plot.minX(), plot.minZ()},
                    {plot.maxX(), plot.minZ()},
                    {plot.minX(), plot.maxZ()},
                    {plot.maxX(), plot.maxZ()},
            };
            for (int[] corner : corners) {
                double reach = Math.hypot(corner[0] - cx, corner[1] - cz) + buffer;
                radius = Math.max(radius, reach);
            }
        }
        return new double[] {cx, cz, radius};
    }

    public static double[][] circlePolygon(double cx, double cz, double radius, int segments) {
        segments = Math.max(12, segments);
        double[][] points = new double[segments][2];
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2 * i) / segments;
            points[i][0] = cx + Math.cos(angle) * radius;
            points[i][1] = cz + Math.sin(angle) * radius;
        }
        return points;
    }

    /** Circumcircle of the union of buffered plot bounds: {centerX, centerZ, radius}. */
    public static double[] circumcircleOfBufferedBounds(List<double[]> boundsList) {
        if (boundsList.isEmpty()) {
            return new double[] {0, 0, 0};
        }
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (double[] bounds : boundsList) {
            minX = Math.min(minX, bounds[0]);
            minZ = Math.min(minZ, bounds[1]);
            maxX = Math.max(maxX, bounds[2]);
            maxZ = Math.max(maxZ, bounds[3]);
        }
        double cx = (minX + maxX) / 2.0;
        double cz = (minZ + maxZ) / 2.0;
        double radius = 0;
        for (double x : new double[] {minX, maxX}) {
            for (double z : new double[] {minZ, maxZ}) {
                radius = Math.max(radius, Math.hypot(x - cx, z - cz));
            }
        }
        return new double[] {cx, cz, radius};
    }

    private static double distanceToRect(double x, double z, PlotRect plot) {
        double dx = 0;
        if (x < plot.minX()) {
            dx = plot.minX() - x;
        } else if (x > plot.maxX()) {
            dx = x - plot.maxX();
        }
        double dz = 0;
        if (z < plot.minZ()) {
            dz = plot.minZ() - z;
        } else if (z > plot.maxZ()) {
            dz = z - plot.maxZ();
        }
        return Math.hypot(dx, dz);
    }
}
