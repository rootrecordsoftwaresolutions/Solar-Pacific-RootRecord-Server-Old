package com.rootrecord.minecraft.rootterritories.service;

import com.rootrecord.minecraft.rootterritories.config.TerritoriesConfig;
import com.rootrecord.minecraft.rootterritories.geom.InfluenceGeometry;
import com.rootrecord.minecraft.rootterritories.geom.InfluenceOwnership;
import com.rootrecord.minecraft.rootterritories.geom.SpawnExclusionZone;
import com.rootrecord.minecraft.rootterritories.geom.UnionContour;
import com.rootrecord.minecraft.rootterritories.model.NationTerritory;
import com.rootrecord.minecraft.rootterritories.model.PlotRect;
import com.rootrecord.minecraft.rootterritories.model.TownTerritory;
import com.rootrecord.minecraft.rootterritories.model.WildernessAt;
import com.rootrecord.minecraft.rootterritories.towny.TownyTerritorySource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class TerritoryService {

    /** Resolved once at first rebuild — Claims XOR Towny, never both. */
    public enum ClaimBackend {
        UNRESOLVED,
        CLAIMS,
        TOWNY,
        NONE
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private TerritoriesConfig config;
    private volatile ClaimBackend claimBackend = ClaimBackend.UNRESOLVED;
    private volatile List<NationTerritory> nations = List.of();
    private volatile List<TownTerritory> independentTowns = List.of();
    private final Map<String, List<NationTerritory>> nationsByWorld = new ConcurrentHashMap<>();
    private final Map<String, List<TownTerritory>> townsByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerZones = new ConcurrentHashMap<>();
    private volatile SpawnExclusionZone spawnZone = SpawnExclusionZone.empty();

    public TerritoryService(JavaPlugin plugin, TerritoriesConfig config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
        reloadSpawnZone();
    }

    public ClaimBackend claimBackend() {
        return resolveClaimBackend();
    }

    /** Towny ring rebuilds / refresh timers only run when Towny is the chosen backend. */
    public boolean usesTownyZones() {
        return resolveClaimBackend() == ClaimBackend.TOWNY;
    }

    private ClaimBackend resolveClaimBackend() {
        ClaimBackend current = claimBackend;
        if (current != ClaimBackend.UNRESOLVED) {
            return current;
        }
        synchronized (this) {
            if (claimBackend != ClaimBackend.UNRESOLVED) {
                return claimBackend;
            }
            boolean claimsPresent = Bukkit.getPluginManager().getPlugin("Root-Claims") != null
                    && Bukkit.getPluginManager().isPluginEnabled("Root-Claims");
            boolean townyPresent = TownyTerritorySource.isAvailable();
            if (claimsPresent) {
                claimBackend = ClaimBackend.CLAIMS;
                logger.info("Claims found - using claims as default claim plugin");
            } else if (townyPresent) {
                claimBackend = ClaimBackend.TOWNY;
                logger.info("Towny found - using Towny as default claim plugin");
            } else {
                claimBackend = ClaimBackend.NONE;
                logger.warning("No claim plugin found - territory zones disabled.");
            }
            return claimBackend;
        }
    }

    private void clearTownyZones() {
        nations = List.of();
        independentTowns = List.of();
        nationsByWorld.clear();
        townsByWorld.clear();
    }

    public void setConfig(TerritoriesConfig config) {
        this.config = config;
    }

    public TerritoriesConfig config() {
        return config;
    }

    public SpawnExclusionZone spawnZone() {
        return spawnZone;
    }

    public void reloadSpawnZone() {
        spawnZone = SpawnExclusionZone.load(plugin);
    }

    public List<NationTerritory> nations() {
        return nations;
    }

    public List<TownTerritory> independentTowns() {
        return independentTowns;
    }

    public Set<String> activeZoneKeys(UUID playerId) {
        return playerZones.getOrDefault(playerId, Set.of());
    }

    public void clearPlayer(UUID playerId) {
        playerZones.remove(playerId);
    }

    public void rebuild(boolean logSummary) {
        reloadSpawnZone();
        if (!config.enabled()) {
            clearTownyZones();
            return;
        }
        ClaimBackend backend = resolveClaimBackend();
        if (backend != ClaimBackend.TOWNY) {
            // Claims (or none): Root-Claims owns claim/territory lookups; skip Towny rescans.
            clearTownyZones();
            return;
        }
        List<NationTerritory> builtNations = TownyTerritorySource.collectNations(
                config.nationCapitalBuffer(),
                config.townBuffer());
        List<TownTerritory> builtTowns = TownyTerritorySource.collectIndependentTowns(config.townBuffer());

        Map<String, List<NationTerritory>> nationsWorld = new HashMap<>();
        for (NationTerritory nation : builtNations) {
            nationsWorld.computeIfAbsent(nation.world(), ignored -> new ArrayList<>()).add(nation);
        }
        Map<String, List<TownTerritory>> townsWorld = new HashMap<>();
        for (TownTerritory town : builtTowns) {
            townsWorld.computeIfAbsent(town.world(), ignored -> new ArrayList<>()).add(town);
        }
        for (List<NationTerritory> list : nationsWorld.values()) {
            list.sort((a, b) -> a.key().compareToIgnoreCase(b.key()));
        }
        for (List<TownTerritory> list : townsWorld.values()) {
            list.sort((a, b) -> a.key().compareToIgnoreCase(b.key()));
        }

        nations = List.copyOf(builtNations);
        independentTowns = List.copyOf(builtTowns);
        nationsByWorld.clear();
        nationsByWorld.putAll(nationsWorld);
        townsByWorld.clear();
        townsByWorld.putAll(townsWorld);

        if (logSummary) {
            long nationCount = builtNations.stream().map(NationTerritory::key).distinct().count();
            int nationRegions = builtNations.stream().mapToInt(nation -> nation.regions().size()).sum();
            int townRegions = builtTowns.stream().mapToInt(town -> town.regions().size()).sum();
            logger.info("Territory zones rebuilt — " + nationCount + " nation(s) (" + nationRegions
                    + " region(s)), " + builtTowns.size() + " independent town(s) (" + townRegions + " region(s)).");
        }
    }

    public Set<String> zonesAt(String world, double x, double z) {
        return zonesAt(world, x, z, 0);
    }

    public Set<String> zonesAt(String world, double x, double z, double extraBuffer) {
        if (world == null || world.isBlank()) {
            return Set.of();
        }
        if (extraBuffer != 0) {
            return legacyZonesAt(world, x, z, extraBuffer);
        }
        Set<String> inside = new LinkedHashSet<>();
        List<NationTerritory> worldNations = nationsByWorld.get(world);
        if (worldNations != null) {
            InfluenceOwnership.winningNationKey(worldNations, world, x, z, spawnZone)
                    .ifPresent(inside::add);
        }
        List<TownTerritory> worldTowns = townsByWorld.get(world);
        if (worldTowns != null) {
            InfluenceOwnership.winningTownKey(worldTowns, world, x, z, spawnZone)
                    .ifPresent(inside::add);
        }
        return inside.isEmpty() ? Set.of() : Set.copyOf(inside);
    }

    private Set<String> legacyZonesAt(String world, double x, double z, double extraBuffer) {
        Set<String> inside = new HashSet<>();
        List<NationTerritory> worldNations = nationsByWorld.get(world);
        if (worldNations != null) {
            for (NationTerritory nation : worldNations) {
                if (nation.contains(x, z, extraBuffer)) {
                    inside.add(nation.key());
                }
            }
        }
        List<TownTerritory> worldTowns = townsByWorld.get(world);
        if (worldTowns != null) {
            for (TownTerritory town : worldTowns) {
                if (town.contains(x, z, extraBuffer)) {
                    inside.add(town.key());
                }
            }
        }
        return inside;
    }

    public double[][] exclusiveNationMapPolygon(NationTerritory nation) {
        double inset = config.mapOutlineInset();
        int step = config.bluemapContourStep();
        List<NationTerritory> worldNations = nationsByWorld.getOrDefault(nation.world(), List.of());
        return UnionContour.compute(
                nation,
                step,
                inset,
                (x, z) -> InfluenceOwnership.nationOwnsAt(nation, worldNations, nation.world(), x, z, spawnZone, inset));
    }

    public double[][] exclusiveTownMapPolygon(TownTerritory town) {
        double inset = config.mapOutlineInset();
        int step = config.bluemapContourStep();
        List<TownTerritory> worldTowns = townsByWorld.getOrDefault(town.world(), List.of());
        return UnionContour.compute(
                town.asContourNation(),
                step,
                inset,
                (x, z) -> InfluenceOwnership.townOwnsAt(town, worldTowns, town.world(), x, z, spawnZone, inset));
    }

    public double[][] exclusiveParticleNationPolygon(NationTerritory nation) {
        int step = config.particleContourStep();
        int griefBuffer = SpawnExclusionZone.readGriefBufferBlocks(plugin);
        List<NationTerritory> worldNations = nationsByWorld.getOrDefault(nation.world(), List.of());
        return UnionContour.compute(
                nation,
                step,
                0,
                (x, z) -> !spawnZone.isNoBuildZone(nation.world(), x, z, griefBuffer)
                        && InfluenceOwnership.nationOwnsAt(
                                nation, worldNations, nation.world(), x, z, spawnZone, 0));
    }

    public double[][] exclusiveParticleTownPolygon(TownTerritory town) {
        int step = config.particleContourStep();
        int griefBuffer = SpawnExclusionZone.readGriefBufferBlocks(plugin);
        List<TownTerritory> worldTowns = townsByWorld.getOrDefault(town.world(), List.of());
        return UnionContour.compute(
                town.asContourNation(),
                step,
                0,
                (x, z) -> !spawnZone.isNoBuildZone(town.world(), x, z, griefBuffer)
                        && InfluenceOwnership.townOwnsAt(
                                town, worldTowns, town.world(), x, z, spawnZone, 0));
    }

    public WildernessAt wildernessAt(String world, double x, double z) {
        if (world == null || world.isBlank()) {
            return WildernessAt.empty();
        }
        if (spawnZone.contains(world, x, z)) {
            return WildernessAt.empty();
        }
        Set<String> townNames = new LinkedHashSet<>();
        Set<String> nationNames = new LinkedHashSet<>();

        List<NationTerritory> worldNations = nationsByWorld.get(world);
        if (worldNations != null) {
            InfluenceOwnership.winningNationKey(worldNations, world, x, z, spawnZone).ifPresent(nationKey -> {
                NationTerritory nation = nationIndex().get(nationKey);
                if (nation == null) {
                    return;
                }
                for (NationTerritory.InfluenceRegion region : nation.regions()) {
                    if (region.isWildernessAt(x, z)) {
                        String townName = region.townName();
                        if (townName != null && !townName.isBlank()) {
                            townNames.add(townName);
                        }
                    }
                }
                if (nation.isWildernessAt(x, z)) {
                    nationNames.add(nation.nationName());
                }
            });
        }

        List<TownTerritory> worldTowns = townsByWorld.get(world);
        if (worldTowns != null) {
            InfluenceOwnership.winningTownKey(worldTowns, world, x, z, spawnZone).ifPresent(townKey -> {
                TownTerritory town = townIndex().get(townKey);
                if (town == null) {
                    return;
                }
                for (NationTerritory.InfluenceRegion region : town.regions()) {
                    if (region.isWildernessAt(x, z)) {
                        townNames.add(town.townName());
                    }
                }
            });
        }

        return new WildernessAt(Set.copyOf(townNames), Set.copyOf(nationNames));
    }

    public void syncPlayerZonesSilently(Player player) {
        if (player == null || !config.enabled() || player.getWorld() == null) {
            return;
        }
        Location loc = player.getLocation();
        double px = Math.floor(loc.getX()) + 0.5;
        double pz = Math.floor(loc.getZ()) + 0.5;
        Set<String> now = zonesAt(loc.getWorld().getName(), px, pz, 0);
        forcePlayerZones(player.getUniqueId(), now);
    }

    public void syncAllOnlinePlayersSilently() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayerZonesSilently(player);
        }
    }

    private void forcePlayerZones(UUID playerId, Set<String> nowInside) {
        if (nowInside.isEmpty()) {
            playerZones.remove(playerId);
        } else {
            playerZones.put(playerId, Set.copyOf(nowInside));
        }
    }

    public void updatePlayerZones(UUID playerId, Set<String> nowInside) {
        Set<String> previous = playerZones.getOrDefault(playerId, Set.of());
        if (previous.equals(nowInside)) {
            return;
        }
        if (nowInside.isEmpty()) {
            playerZones.remove(playerId);
        } else {
            playerZones.put(playerId, Set.copyOf(nowInside));
        }
    }

    public Map<String, NationTerritory> nationIndex() {
        Map<String, NationTerritory> index = new HashMap<>();
        for (NationTerritory nation : nations) {
            index.putIfAbsent(nation.key(), nation);
        }
        return Collections.unmodifiableMap(index);
    }

    public Map<String, TownTerritory> townIndex() {
        Map<String, TownTerritory> index = new HashMap<>();
        for (TownTerritory town : independentTowns) {
            index.putIfAbsent(town.key(), town);
        }
        return Collections.unmodifiableMap(index);
    }

    public boolean isInsideForeignInfluence(String world, double x, double z, String ignoreTownName) {
        if (spawnZone.contains(world, x, z)) {
            return false;
        }
        String ignoreTownKey = ignoreTownName == null
                ? null
                : "town:" + ignoreTownName.toLowerCase(Locale.ROOT);
        for (String key : zonesAt(world, x, z)) {
            if (ignoreTownKey != null && key.equalsIgnoreCase(ignoreTownKey)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public boolean canCreateTownAt(Location location, String newTownName) {
        if (!config.enforceTownRules() || location == null || location.getWorld() == null) {
            return true;
        }
        double x = location.getBlockX() + 0.5;
        double z = location.getBlockZ() + 0.5;
        return !isInsideForeignInfluence(location.getWorld().getName(), x, z, newTownName);
    }

    public boolean canTownJoinNation(Object townObject, Object nationObject) {
        if (!config.enforceTownRules() || townObject == null || nationObject == null) {
            return true;
        }
        List<PlotRect> townPlots = TownyTerritorySource.plotsFromTown(townObject);
        if (townPlots.isEmpty()) {
            return false;
        }
        double townBuffer = config.townBuffer();
        List<NationTerritory> liveNations = TownyTerritorySource.nationTerritories(
                nationObject,
                config.nationCapitalBuffer(),
                config.townBuffer());
        for (NationTerritory nation : liveNations) {
            for (NationTerritory.InfluenceRegion region : nation.regions()) {
                if (InfluenceGeometry.regionsTouch(townPlots, townBuffer, region.plots(), region.buffer())) {
                    return true;
                }
            }
        }
        return false;
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
