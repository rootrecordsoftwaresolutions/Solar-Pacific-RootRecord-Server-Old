package com.rootrecord.minecraft.rootterritories.model;

import com.rootrecord.minecraft.rootterritories.geom.PlotGeometry;
import com.rootrecord.minecraft.rootterritories.geom.UnionContour;

import java.util.List;
import java.util.Locale;

/** One nation's combined influence in a world (capital hub + member-town extensions). */
public final class NationTerritory {

    /** Plots plus outward buffer measured from plot edges. */
    public record InfluenceRegion(String townName, List<PlotRect> plots, double buffer) {

        public boolean contains(double x, double z, double extraBuffer) {
            return PlotGeometry.withinBuffer(x, z, plots, buffer + extraBuffer);
        }

        public boolean contains(double x, double z) {
            return contains(x, z, 0);
        }

        /** Inside the influence buffer but outside claimed town plots. */
        public boolean isWildernessAt(double x, double z) {
            return contains(x, z) && !PlotGeometry.insidePlots(x, z, plots);
        }

        public double[] bufferedBounds() {
            return PlotGeometry.bufferedBounds(plots, buffer);
        }

        public double[] mapCircle() {
            return PlotGeometry.influenceCircle(plots, buffer);
        }

        public String markerSlug() {
            String slug = townName == null
                    ? "region"
                    : townName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            double[] circle = mapCircle();
            long bucket = Math.round(circle[0] / 64.0) ^ (Math.round(circle[1] / 64.0) << 16);
            return slug + "-" + Long.toUnsignedString(bucket, 36);
        }
    }

    private final String nationName;
    private final String world;
    private final String key;
    private final List<InfluenceRegion> regions;

    public NationTerritory(String nationName, String world, List<InfluenceRegion> regions) {
        this.nationName = nationName;
        this.world = world;
        this.regions = List.copyOf(regions);
        this.key = "nation:" + nationName.toLowerCase(Locale.ROOT);
    }

    public String nationName() {
        return nationName;
    }

    public String world() {
        return world;
    }

    public String key() {
        return key;
    }

    public List<InfluenceRegion> regions() {
        return regions;
    }

    public boolean contains(double x, double z) {
        return contains(x, z, 0);
    }

    public boolean contains(double x, double z, double extraBuffer) {
        for (InfluenceRegion region : regions) {
            if (region.contains(x, z, extraBuffer)) {
                return true;
            }
        }
        return false;
    }

    /** Inside nation influence but outside every member town's claimed plots. */
    public boolean isWildernessAt(double x, double z) {
        if (!contains(x, z)) {
            return false;
        }
        for (InfluenceRegion region : regions) {
            if (PlotGeometry.insidePlots(x, z, region.plots())) {
                return false;
            }
        }
        return true;
    }

    public String bluemapMarkerId() {
        return key.replace(':', '-');
    }

    /** Merged outer outline of all influence regions for the map. */
    public double[][] unifiedMapPolygon(int step, double mapInset) {
        return UnionContour.compute(this, step, mapInset);
    }

    public String bluemapLabel() {
        return nationName;
    }
}
