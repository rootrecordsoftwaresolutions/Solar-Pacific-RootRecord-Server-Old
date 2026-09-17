package com.rootrecord.minecraft.rootterritories.bluemap;

import com.rootrecord.minecraft.rootterritories.config.TerritoriesConfig;
import com.rootrecord.minecraft.rootterritories.geom.SpawnExclusionZone;
import com.rootrecord.minecraft.rootterritories.model.NationTerritory;
import com.rootrecord.minecraft.rootterritories.model.TownTerritory;
import com.rootrecord.minecraft.rootterritories.service.TerritoryService;
import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Consumer;

/** Nation and independent-town merged outlines on BlueMap, plus spawn protection polygon. */
public final class BlueMapTerritoryMarkers {

    private static final String SPAWN_SET_ID = "root-spawn";
    private static final String SPAWN_GRIEF_ID = "spawn-grief";
    private static final String SPAWN_WALL_ID = "spawn-wall";
    private static final float[] NO_FILL = {0, 0, 0, 0};

    private final JavaPlugin plugin;
    private final TerritoryService territories;
    private Consumer<BlueMapAPI> enableHook;

    public BlueMapTerritoryMarkers(JavaPlugin plugin, TerritoryService territories) {
        this.plugin = plugin;
        this.territories = territories;
    }

    public void register() {
        if (!blueMapPresent()) {
            plugin.getLogger().info("BlueMap not installed — territory map outlines skipped.");
            return;
        }
        try {
            enableHook = this::syncAll;
            BlueMapAPI.onEnable(enableHook);
            BlueMapAPI.getInstance().ifPresent(this::syncAll);
            plugin.getLogger().info("BlueMap territory markers registered.");
        } catch (NoClassDefFoundError | Exception ex) {
            plugin.getLogger().info("BlueMap API unavailable — territory map outlines skipped.");
            enableHook = null;
        }
    }

    public void unregister() {
        if (enableHook == null || !blueMapPresent()) {
            enableHook = null;
            return;
        }
        try {
            BlueMapAPI.onDisable(enableHook);
        } catch (NoClassDefFoundError | Exception ignored) {
            // BlueMap already gone
        }
        enableHook = null;
    }

    public void syncIfPresent() {
        if (!blueMapPresent()) {
            return;
        }
        try {
            BlueMapAPI.getInstance().ifPresent(this::syncAll);
        } catch (NoClassDefFoundError | Exception ignored) {
            // Claims / no BlueMap jar
        }
    }

    private static boolean blueMapPresent() {
        return Bukkit.getPluginManager().getPlugin("BlueMap") != null;
    }

    public void syncAll(BlueMapAPI api) {
        TerritoriesConfig cfg = territories.config();
        if (!cfg.enabled() || !cfg.bluemapEnabled()) {
            return;
        }
        List<NationTerritory> nations = territories.nations();
        List<TownTerritory> towns = territories.independentTowns();
        SpawnExclusionZone spawn = territories.spawnZone();
        for (BlueMapMap map : api.getMaps()) {
            String mapId = map.getId();
            MarkerSet nationSet = markerSet(map, cfg.bluemapNationSetId(), cfg.bluemapNationsLabel());
            nationSet.getMarkers().clear();
            for (NationTerritory nation : nations) {
                if (!mapIdMatches(mapId, nation.world()) || nation.regions().isEmpty()) {
                    continue;
                }
                ShapeMarker marker = mergedOutlineMarker(
                        nation.bluemapLabel(),
                        territories.exclusiveNationMapPolygon(nation),
                        cfg.nationLine(),
                        cfg.nationFill(),
                        cfg,
                        cfg.bluemapShapeY());
                if (marker != null) {
                    nationSet.getMarkers().put(nation.bluemapMarkerId(), marker);
                }
            }

            MarkerSet townSet = markerSet(map, cfg.bluemapTownSetId(), cfg.bluemapTownsLabel());
            townSet.getMarkers().clear();
            for (TownTerritory town : towns) {
                if (!mapIdMatches(mapId, town.world()) || town.regions().isEmpty()) {
                    continue;
                }
                ShapeMarker marker = mergedOutlineMarker(
                        town.bluemapLabel(),
                        territories.exclusiveTownMapPolygon(town),
                        cfg.townLine(),
                        cfg.townFill(),
                        cfg,
                        cfg.bluemapShapeY());
                if (marker != null) {
                    townSet.getMarkers().put(town.bluemapMarkerId(), marker);
                }
            }

            syncSpawnMarkers(map, mapId, spawn, cfg);
        }
    }

    private void syncSpawnMarkers(BlueMapMap map, String mapId, SpawnExclusionZone spawn, TerritoriesConfig cfg) {
        MarkerSet spawnSet = markerSet(map, SPAWN_SET_ID, "Spawn");
        spawnSet.getMarkers().clear();
        if (spawn == null || spawn.isEmpty() || !mapIdMatches(mapId, spawn.worldName())) {
            return;
        }
        int griefBuffer = SpawnExclusionZone.readGriefBufferBlocks(plugin);
        float shapeY = cfg.bluemapSpawnShapeY();

        ShapeMarker grief = mergedOutlineMarker(
                "Spawn — no build/break",
                spawn.mapGriefPolygon(griefBuffer),
                cfg.spawnGriefLine(),
                cfg.spawnGriefFill(),
                cfg,
                shapeY);
        if (grief != null) {
            spawnSet.getMarkers().put(SPAWN_GRIEF_ID, grief);
        }

        ShapeMarker wall = mergedOutlineMarker(
                "Spawn wall — PvP line",
                spawn.mapPolygon(),
                cfg.spawnWallLine(),
                NO_FILL,
                cfg,
                shapeY);
        if (wall != null) {
            spawnSet.getMarkers().put(SPAWN_WALL_ID, wall);
        }
    }

    private static MarkerSet markerSet(BlueMapMap map, String id, String label) {
        MarkerSet existing = map.getMarkerSets().get(id);
        if (existing != null) {
            return existing;
        }
        MarkerSet created = MarkerSet.builder()
                .label(label)
                .toggleable(true)
                .defaultHidden(false)
                .build();
        map.getMarkerSets().put(id, created);
        return created;
    }

    private static ShapeMarker mergedOutlineMarker(
            String label,
            double[][] polygon,
            float[] lineRgba,
            float[] fillRgba,
            TerritoriesConfig cfg) {
        return mergedOutlineMarker(label, polygon, lineRgba, fillRgba, cfg, cfg.bluemapShapeY());
    }

    private static ShapeMarker mergedOutlineMarker(
            String label,
            double[][] polygon,
            float[] lineRgba,
            float[] fillRgba,
            TerritoriesConfig cfg,
            float shapeY) {
        if (polygon.length < 3) {
            return null;
        }
        Vector2d[] points = new Vector2d[polygon.length];
        for (int i = 0; i < polygon.length; i++) {
            points[i] = new Vector2d(polygon[i][0], polygon[i][1]);
        }
        Shape shape = new Shape(points);
        ShapeMarker marker = new ShapeMarker(label, shape, shapeY);
        marker.setLineColor(color(lineRgba));
        marker.setFillColor(color(fillRgba));
        marker.setLineWidth(cfg.bluemapLineWidth());
        marker.setDepthTestEnabled(cfg.bluemapDepthTest());
        marker.centerPosition();
        return marker;
    }

    private static Color color(float[] rgba) {
        int r = rgba.length > 0 ? (int) rgba[0] : 255;
        int g = rgba.length > 1 ? (int) rgba[1] : 255;
        int b = rgba.length > 2 ? (int) rgba[2] : 255;
        float a = rgba.length > 3 ? rgba[3] : 1f;
        return new Color(r, g, b, a);
    }

    private static boolean mapIdMatches(String mapId, String worldName) {
        if (mapId == null || worldName == null) {
            return false;
        }
        if (mapId.equalsIgnoreCase(worldName)) {
            return true;
        }
        if ("world".equalsIgnoreCase(mapId) && (
                "world".equalsIgnoreCase(worldName)
                        || "RootMC".equalsIgnoreCase(worldName)
                        || "overworld".equalsIgnoreCase(worldName))) {
            return true;
        }
        if ("the_nether".equalsIgnoreCase(mapId) && worldName.toLowerCase().contains("nether")) {
            return true;
        }
        if ("the_end".equalsIgnoreCase(mapId) && worldName.toLowerCase().contains("end")) {
            return true;
        }
        return false;
    }
}
