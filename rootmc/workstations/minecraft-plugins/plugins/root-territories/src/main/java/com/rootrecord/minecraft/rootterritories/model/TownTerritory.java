package com.rootrecord.minecraft.rootterritories.model;

import com.rootrecord.minecraft.rootterritories.geom.PlotGeometry;
import com.rootrecord.minecraft.rootterritories.geom.UnionContour;

import java.util.List;
import java.util.Locale;

/** Independent town influence (not part of a nation). */
public final class TownTerritory {

    private final String townName;
    private final String world;
    private final String key;
    private final List<NationTerritory.InfluenceRegion> regions;

    public TownTerritory(String townName, String world, List<NationTerritory.InfluenceRegion> regions) {
        this.townName = townName;
        this.world = world;
        this.regions = List.copyOf(regions);
        this.key = "town:" + townName.toLowerCase(Locale.ROOT);
    }

    public String townName() {
        return townName;
    }

    public String world() {
        return world;
    }

    public String key() {
        return key;
    }

    public List<NationTerritory.InfluenceRegion> regions() {
        return regions;
    }

    public List<PlotRect> allPlots() {
        return regions.stream().flatMap(region -> region.plots().stream()).toList();
    }

    public boolean contains(double x, double z) {
        return contains(x, z, 0);
    }

    public boolean contains(double x, double z, double extraBuffer) {
        for (NationTerritory.InfluenceRegion region : regions) {
            if (region.contains(x, z, extraBuffer)) {
                return true;
            }
        }
        return false;
    }

    public String bluemapMarkerId() {
        return key.replace(':', '-');
    }

    public String bluemapLabel() {
        return townName;
    }

    public double[][] unifiedMapPolygon(int step, double mapInset) {
        return UnionContour.compute(asContourNation(), step, mapInset);
    }

    /** Adapter for shared contour logic. */
    public NationTerritory asContourNation() {
        return new NationTerritory(townName, world, regions);
    }
}
