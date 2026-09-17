package com.rootrecord.minecraft.rootterritories.towny;

import com.rootrecord.minecraft.rootterritories.geom.PlotClusters;
import com.rootrecord.minecraft.rootterritories.model.NationTerritory;
import com.rootrecord.minecraft.rootterritories.model.PlotRect;
import com.rootrecord.minecraft.rootterritories.model.TownTerritory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads Towny nations/towns/plots via reflection. */
public final class TownyTerritorySource {

    private TownyTerritorySource() {}

    public static boolean isAvailable() {
        return TownyReflection.isAvailable();
    }

    public static List<NationTerritory> collectNations(double capitalBuffer, double extensionBuffer) {
        List<NationTerritory> nations = new ArrayList<>();
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return nations;
        }
        Object nationObjects = TownyReflection.invokeNoArg(api, "getNations");
        for (Object nation : TownyReflection.asCollection(nationObjects)) {
            String nationName = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
            if (nationName == null) {
                continue;
            }
            Object capital = TownyReflection.invokeNoArg(nation, "getCapital");
            String capitalName = capital == null
                    ? null
                    : TownyReflection.stringOrNull(TownyReflection.invokeNoArg(capital, "getName"));

            Map<String, List<NationTerritory.InfluenceRegion>> regionsByWorld = new LinkedHashMap<>();
            Object towns = TownyReflection.invokeNoArg(nation, "getTowns");
            for (Object town : TownyReflection.asCollection(towns)) {
                addTownRegions(regionsByWorld, town, capitalName, capitalBuffer, extensionBuffer);
            }
            for (Map.Entry<String, List<NationTerritory.InfluenceRegion>> entry : regionsByWorld.entrySet()) {
                nations.add(new NationTerritory(nationName, entry.getKey(), entry.getValue()));
            }
        }
        return nations;
    }

    public static List<TownTerritory> collectIndependentTowns(double townBuffer) {
        List<TownTerritory> towns = new ArrayList<>();
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return towns;
        }
        Object townObjects = TownyReflection.invokeNoArg(api, "getTowns");
        for (Object town : TownyReflection.asCollection(townObjects)) {
            if (hasNation(town)) {
                continue;
            }
            String townName = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName"));
            if (townName == null) {
                continue;
            }
            Map<String, List<NationTerritory.InfluenceRegion>> regionsByWorld = new LinkedHashMap<>();
            addTownRegions(regionsByWorld, town, null, townBuffer, townBuffer);
            for (Map.Entry<String, List<NationTerritory.InfluenceRegion>> entry : regionsByWorld.entrySet()) {
                towns.add(new TownTerritory(townName, entry.getKey(), entry.getValue()));
            }
        }
        return towns;
    }

    public static boolean hasNation(Object town) {
        if (town == null) {
            return false;
        }
        Object nation = TownyReflection.invokeNoArg(town, "getNation");
        if (nation == null) {
            return false;
        }
        String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
        return name != null && !name.isBlank();
    }

    public static String townName(Object town) {
        return town == null ? null : TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName"));
    }

    public static String nationName(Object nation) {
        return nation == null ? null : TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
    }

    public static List<NationTerritory> nationTerritories(
            Object nation,
            double capitalBuffer,
            double extensionBuffer) {
        if (nation == null) {
            return List.of();
        }
        String nationName = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
        if (nationName == null) {
            return List.of();
        }
        Object capital = TownyReflection.invokeNoArg(nation, "getCapital");
        String capitalName = capital == null
                ? null
                : TownyReflection.stringOrNull(TownyReflection.invokeNoArg(capital, "getName"));

        Map<String, List<NationTerritory.InfluenceRegion>> regionsByWorld = new LinkedHashMap<>();
        Object towns = TownyReflection.invokeNoArg(nation, "getTowns");
        for (Object town : TownyReflection.asCollection(towns)) {
            addTownRegions(regionsByWorld, town, capitalName, capitalBuffer, extensionBuffer);
        }
        List<NationTerritory> built = new ArrayList<>();
        for (Map.Entry<String, List<NationTerritory.InfluenceRegion>> entry : regionsByWorld.entrySet()) {
            built.add(new NationTerritory(nationName, entry.getKey(), entry.getValue()));
        }
        return built;
    }

    public static List<PlotRect> plotsFromTown(Object town) {
        List<PlotRect> plots = new ArrayList<>();
        for (List<PlotRect> worldPlots : plotsByWorld(town).values()) {
            plots.addAll(worldPlots);
        }
        return plots;
    }

    private static void addTownRegions(
            Map<String, List<NationTerritory.InfluenceRegion>> regionsByWorld,
            Object town,
            String capitalName,
            double capitalBuffer,
            double extensionBuffer) {
        String townName = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName"));
        if (townName == null) {
            return;
        }
        boolean isCapital = capitalName != null && capitalName.equalsIgnoreCase(townName);
        double buffer = isCapital ? capitalBuffer : extensionBuffer;
        Map<String, List<PlotRect>> townByWorld = plotsByWorld(town);
        for (Map.Entry<String, List<PlotRect>> entry : townByWorld.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            for (List<PlotRect> cluster : PlotClusters.split(entry.getValue())) {
                if (cluster.isEmpty()) {
                    continue;
                }
                regionsByWorld
                        .computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(new NationTerritory.InfluenceRegion(townName, cluster, buffer));
            }
        }
    }

    static Map<String, List<PlotRect>> plotsByWorld(Object town) {
        Map<String, List<PlotRect>> byWorld = new LinkedHashMap<>();
        Object townBlocks = TownyReflection.invokeNoArg(town, "getTownBlocks");
        appendBlocks(byWorld, TownyReflection.asCollection(townBlocks));
        if (byWorld.isEmpty()) {
            Object blocksMap = TownyReflection.invokeNoArg(town, "getTownBlocksMap");
            if (blocksMap instanceof Map<?, ?> map) {
                appendBlocks(byWorld, map.values());
            }
        }
        return byWorld;
    }

    private static void appendBlocks(Map<String, List<PlotRect>> byWorld, Collection<?> blocks) {
        for (Object townBlock : blocks) {
            PlotRect rect = plotRect(townBlock);
            if (rect == null) {
                continue;
            }
            byWorld.computeIfAbsent(rect.world(), ignored -> new ArrayList<>()).add(rect);
        }
    }

    static PlotRect plotRect(Object townBlock) {
        if (townBlock == null) {
            return null;
        }
        Object coord = TownyReflection.invokeNoArg(townBlock, "getWorldCoord", "getCoord");
        if (coord == null) {
            return null;
        }
        String world = worldName(coord, townBlock);
        if (world == null || world.isBlank()) {
            return null;
        }
        int tx = TownyReflection.intOrZero(TownyReflection.invokeNoArg(coord, "getX"));
        int tz = TownyReflection.intOrZero(TownyReflection.invokeNoArg(coord, "getZ"));
        int minX = tx * 16;
        int minZ = tz * 16;
        return new PlotRect(world, minX, minZ, minX + 15, minZ + 15);
    }

    private static String worldName(Object coord, Object townBlock) {
        String fromCoord = TownyReflection.stringOrNull(
                TownyReflection.invokeNoArg(coord, "getWorldName", "getWorld"));
        if (fromCoord != null) {
            return fromCoord;
        }
        Object world = TownyReflection.invokeNoArg(townBlock, "getWorld");
        if (world != null) {
            Object name = TownyReflection.invokeNoArg(world, "getName");
            if (name != null) {
                return String.valueOf(name);
            }
        }
        return null;
    }
}
