package com.rootrecord.minecraft.rootterritories.geom;

import com.rootrecord.minecraft.rootterritories.model.NationTerritory;
import com.rootrecord.minecraft.rootterritories.model.TownTerritory;

import java.util.List;
import java.util.Optional;

/** Picks a single nation/town owner per point so influence rings meet without overlap. */
public final class InfluenceOwnership {

    private InfluenceOwnership() {}

    public static Optional<String> winningNationKey(
            List<NationTerritory> nations,
            String world,
            double x,
            double z,
            SpawnExclusionZone spawn) {
        if (blockedBySpawn(spawn, world, x, z)) {
            return Optional.empty();
        }
        NationTerritory winner = null;
        double bestDistance = Double.MAX_VALUE;
        for (NationTerritory nation : nations) {
            if (!world.equals(nation.world()) || !nation.contains(x, z)) {
                continue;
            }
            double distance = minDistanceToRegions(nation.regions(), x, z);
            if (winner == null
                    || distance < bestDistance
                    || (distance == bestDistance && nation.key().compareToIgnoreCase(winner.key()) < 0)) {
                winner = nation;
                bestDistance = distance;
            }
        }
        return winner == null ? Optional.empty() : Optional.of(winner.key());
    }

    public static Optional<String> winningTownKey(
            List<TownTerritory> towns,
            String world,
            double x,
            double z,
            SpawnExclusionZone spawn) {
        if (blockedBySpawn(spawn, world, x, z)) {
            return Optional.empty();
        }
        TownTerritory winner = null;
        double bestDistance = Double.MAX_VALUE;
        for (TownTerritory town : towns) {
            if (!world.equals(town.world()) || !town.contains(x, z)) {
                continue;
            }
            double distance = minDistanceToRegions(town.regions(), x, z);
            if (winner == null
                    || distance < bestDistance
                    || (distance == bestDistance && town.key().compareToIgnoreCase(winner.key()) < 0)) {
                winner = town;
                bestDistance = distance;
            }
        }
        return winner == null ? Optional.empty() : Optional.of(winner.key());
    }

    public static boolean nationOwnsAt(
            NationTerritory nation,
            List<NationTerritory> nations,
            String world,
            double x,
            double z,
            SpawnExclusionZone spawn,
            double mapInset) {
        if (!nation.world().equals(world) || !nation.contains(x, z, -mapInset)) {
            return false;
        }
        return winningNationKey(nations, world, x, z, spawn)
                .map(key -> key.equalsIgnoreCase(nation.key()))
                .orElse(false);
    }

    public static boolean townOwnsAt(
            TownTerritory town,
            List<TownTerritory> towns,
            String world,
            double x,
            double z,
            SpawnExclusionZone spawn,
            double mapInset) {
        if (!town.world().equals(world) || !town.contains(x, z, -mapInset)) {
            return false;
        }
        return winningTownKey(towns, world, x, z, spawn)
                .map(key -> key.equalsIgnoreCase(town.key()))
                .orElse(false);
    }

    private static boolean blockedBySpawn(SpawnExclusionZone spawn, String world, double x, double z) {
        return spawn != null && !spawn.isEmpty() && spawn.contains(world, x, z);
    }

    private static double minDistanceToRegions(List<NationTerritory.InfluenceRegion> regions, double x, double z) {
        double best = Double.MAX_VALUE;
        for (NationTerritory.InfluenceRegion region : regions) {
            best = Math.min(best, PlotGeometry.distanceToPlots(x, z, region.plots()));
        }
        return best;
    }
}
