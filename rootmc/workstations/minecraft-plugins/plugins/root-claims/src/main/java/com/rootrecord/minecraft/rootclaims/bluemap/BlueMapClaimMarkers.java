package com.rootrecord.minecraft.rootclaims.bluemap;

import com.flowpowered.math.vector.Vector2d;
import com.rootrecord.minecraft.rootclaims.ClaimRecord;
import com.rootrecord.minecraft.rootclaims.ClaimStore;
import com.rootrecord.minecraft.rootclaims.ClaimUnionGeometry;
import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * BlueMap shapes per owner: outer territory ring (claim radius + buffer), then
 * inner claim union on top - two concentric fills when buffer &gt; 0.
 */
public final class BlueMapClaimMarkers {

    private static final String SET_ID = "root-claims";

    private final RootClaimsPlugin plugin;
    private final ClaimStore store;
    private Consumer<BlueMapAPI> enableHook;
    private BukkitTask pendingSync;

    public BlueMapClaimMarkers(RootClaimsPlugin plugin, ClaimStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void register() {
        if (Bukkit.getPluginManager().getPlugin("BlueMap") == null) {
            plugin.getLogger().info("BlueMap not installed - claim map fills skipped.");
            return;
        }
        enableHook = this::syncAll;
        BlueMapAPI.onEnable(enableHook);
        BlueMapAPI.getInstance().ifPresent(this::syncAll);
        plugin.getLogger().info("BlueMap claim markers registered.");
    }

    public void unregister() {
        if (pendingSync != null) {
            pendingSync.cancel();
            pendingSync = null;
        }
        if (enableHook != null) {
            BlueMapAPI.onDisable(enableHook);
            enableHook = null;
        }
    }

    public void scheduleSync() {
        if (!plugin.bluemapEnabled() || Bukkit.getPluginManager().getPlugin("BlueMap") == null) {
            return;
        }
        if (pendingSync != null) {
            pendingSync.cancel();
        }
        pendingSync = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSync = null;
            BlueMapAPI.getInstance().ifPresent(this::syncAll);
        }, 20L);
    }

    public void syncAll(BlueMapAPI api) {
        if (!plugin.bluemapEnabled()) {
            return;
        }
        Map<UUID, List<ClaimRecord>> byOwner = ClaimUnionGeometry.groupByOwner(store.all());
        int step = Math.max(2, plugin.bluemapContourStep());
        float shapeY = plugin.bluemapShapeY();
        int buffer = Math.max(0, plugin.territoryBufferBlocks());
        boolean showTerritory = plugin.bluemapTerritoryEnabled() && buffer > 0;
        for (BlueMapMap map : api.getMaps()) {
            MarkerSet set = markerSet(map);
            set.getMarkers().clear();
            String mapId = map.getId();
            for (Map.Entry<UUID, List<ClaimRecord>> entry : byOwner.entrySet()) {
                List<ClaimRecord> owned = filterWorld(entry.getValue(), mapId);
                if (owned.isEmpty()) {
                    continue;
                }
                String ownerName = owned.get(0).ownerName();
                Color line = ownerColor(entry.getKey(), 1f);
                Color claimFill = ownerColor(entry.getKey(), plugin.bluemapFillAlpha());
                Color territoryFill = ownerColor(entry.getKey(), plugin.bluemapTerritoryFillAlpha());
                int added = 0;

                if (showTerritory) {
                    List<double[][]> territoryLoops = ClaimUnionGeometry.outerLoops(owned, step, buffer);
                    int part = 0;
                    for (double[][] loop : territoryLoops) {
                        if (loop.length < 3) {
                            continue;
                        }
                        String label = ownerName + " territory"
                                + (territoryLoops.size() > 1 ? " (" + (++part) + ")" : "");
                        ShapeMarker marker = shapeMarker(
                                label,
                                loop,
                                line,
                                territoryFill,
                                shapeY,
                                plugin.bluemapTerritoryLineWidth());
                        if (marker != null) {
                            String id = "territory-" + entry.getKey()
                                    + (territoryLoops.size() > 1 ? "-" + part : "");
                            set.getMarkers().put(id, marker);
                            added++;
                        }
                    }
                }

                List<double[][]> claimLoops = ClaimUnionGeometry.outerLoops(owned, step, 0);
                int part = 0;
                for (double[][] loop : claimLoops) {
                    if (loop.length < 3) {
                        continue;
                    }
                    String label = ownerName
                            + (claimLoops.size() > 1 ? " (" + (++part) + ")" : "");
                    ShapeMarker marker = shapeMarker(
                            label,
                            loop,
                            line,
                            claimFill,
                            shapeY,
                            plugin.bluemapLineWidth());
                    if (marker != null) {
                        String id = "claim-" + entry.getKey()
                                + (claimLoops.size() > 1 ? "-" + part : "");
                        set.getMarkers().put(id, marker);
                        added++;
                    }
                }
                if (added > 0) {
                    plugin.getLogger().info("BlueMap " + mapId + ": " + added
                            + " shape(s) for " + ownerName
                            + (showTerritory ? " (claim+territory)" : ""));
                }
            }
        }
    }

    private List<ClaimRecord> filterWorld(List<ClaimRecord> owned, String mapId) {
        List<ClaimRecord> out = new ArrayList<>();
        for (ClaimRecord claim : owned) {
            if (mapMatchesWorld(mapId, claim.key().world())) {
                out.add(claim);
            }
        }
        return out;
    }

    private MarkerSet markerSet(BlueMapMap map) {
        MarkerSet existing = map.getMarkerSets().get(SET_ID);
        if (existing != null) {
            return existing;
        }
        MarkerSet created = MarkerSet.builder()
                .label(plugin.bluemapSetLabel())
                .toggleable(true)
                .defaultHidden(false)
                .build();
        map.getMarkerSets().put(SET_ID, created);
        return created;
    }

    private ShapeMarker shapeMarker(
            String label,
            double[][] polygon,
            Color line,
            Color fill,
            float shapeY,
            int lineWidth) {
        Vector2d[] points = new Vector2d[polygon.length];
        for (int i = 0; i < polygon.length; i++) {
            points[i] = new Vector2d(polygon[i][0], polygon[i][1]);
        }
        Shape shape = new Shape(points);
        ShapeMarker marker = new ShapeMarker(label, shape, shapeY);
        marker.setLineColor(line);
        marker.setFillColor(fill);
        marker.setLineWidth(Math.max(1, lineWidth));
        marker.setDepthTestEnabled(false);
        marker.centerPosition();
        return marker;
    }

    /** Stable pastel-ish color from owner UUID. */
    private static Color ownerColor(UUID ownerId, float alpha) {
        int h = ownerId.hashCode();
        int r = 64 + ((h >>> 16) & 0x7F);
        int g = 64 + ((h >>> 8) & 0x7F);
        int b = 64 + (h & 0x7F);
        return new Color(r, g, b, alpha);
    }

    private static boolean mapMatchesWorld(String mapId, String worldName) {
        if (mapId == null || worldName == null) {
            return false;
        }
        if (mapId.equalsIgnoreCase(worldName)) {
            return true;
        }
        // Gen2 BlueMap map id "gen2" renders Bukkit world "world"
        if ("gen2".equalsIgnoreCase(mapId) && "world".equalsIgnoreCase(worldName)) {
            return true;
        }
        if ("world".equalsIgnoreCase(mapId) && (
                "world".equalsIgnoreCase(worldName)
                        || "RootMC".equalsIgnoreCase(worldName)
                        || "overworld".equalsIgnoreCase(worldName))) {
            return true;
        }
        String lower = worldName.toLowerCase();
        if ("the_nether".equalsIgnoreCase(mapId) && lower.contains("nether")) {
            return true;
        }
        return "the_end".equalsIgnoreCase(mapId) && lower.contains("end");
    }
}
