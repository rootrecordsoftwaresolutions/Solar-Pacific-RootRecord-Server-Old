package com.rootrecord.minecraft.rootterritories.geom;

import com.rootrecord.minecraft.rootterritories.model.PlotRect;

import java.util.ArrayList;
import java.util.List;

/** Splits town plots into connected clusters (adjacent townblocks). */
public final class PlotClusters {

    private PlotClusters() {}

    public static List<List<PlotRect>> split(List<PlotRect> plots) {
        if (plots.isEmpty()) {
            return List.of();
        }
        List<List<PlotRect>> clusters = new ArrayList<>();
        boolean[] visited = new boolean[plots.size()];
        for (int i = 0; i < plots.size(); i++) {
            if (visited[i]) {
                continue;
            }
            List<PlotRect> cluster = new ArrayList<>();
            floodFill(plots, visited, i, cluster);
            clusters.add(cluster);
        }
        return clusters;
    }

    private static void floodFill(List<PlotRect> plots, boolean[] visited, int index, List<PlotRect> cluster) {
        visited[index] = true;
        cluster.add(plots.get(index));
        for (int i = 0; i < plots.size(); i++) {
            if (!visited[i] && adjacent(plots.get(index), plots.get(i))) {
                floodFill(plots, visited, i, cluster);
            }
        }
    }

    private static boolean adjacent(PlotRect a, PlotRect b) {
        if (!a.world().equals(b.world())) {
            return false;
        }
        boolean xTouch = a.maxX() + 1 == b.minX() || b.maxX() + 1 == a.minX();
        boolean zTouch = a.maxZ() + 1 == b.minZ() || b.maxZ() + 1 == a.minZ();
        boolean xOverlap = a.minX() <= b.maxX() && b.minX() <= a.maxX();
        boolean zOverlap = a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
        return (xTouch && zOverlap) || (zTouch && xOverlap);
    }
}
