package com.rootrecord.minecraft.rootterritories.geom;

import com.rootrecord.minecraft.rootterritories.model.PlotRect;

import java.util.List;

/** Influence overlap checks using plot edges + buffers. */
public final class InfluenceGeometry {

    private InfluenceGeometry() {}

    public static double minPlotEdgeGap(List<PlotRect> plotsA, List<PlotRect> plotsB) {
        if (plotsA.isEmpty() || plotsB.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double best = Double.MAX_VALUE;
        for (PlotRect a : plotsA) {
            for (PlotRect b : plotsB) {
                best = Math.min(best, rectToRect(a, b));
            }
        }
        return best;
    }

    /** True when buffered plot areas touch or overlap (measured from plot edges). */
    public static boolean regionsTouch(List<PlotRect> plotsA, double bufferA, List<PlotRect> plotsB, double bufferB) {
        double gap = minPlotEdgeGap(plotsA, plotsB);
        return gap <= bufferA + bufferB + 0.01;
    }

    public static boolean pointInsideInfluence(double x, double z, List<PlotRect> plots, double buffer) {
        return PlotGeometry.withinBuffer(x, z, plots, buffer);
    }

    private static double rectToRect(PlotRect a, PlotRect b) {
        double best = Double.MAX_VALUE;
        for (double x : new double[] {a.minX(), a.maxX()}) {
            for (double z : new double[] {a.minZ(), a.maxZ()}) {
                best = Math.min(best, distanceToRect(x, z, b));
            }
        }
        for (double x : new double[] {b.minX(), b.maxX()}) {
            for (double z : new double[] {b.minZ(), b.maxZ()}) {
                best = Math.min(best, distanceToRect(x, z, a));
            }
        }
        return best;
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
