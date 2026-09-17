package com.rootrecord.minecraft.rootspawn;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Load/save polygon waypoints under plugins/RootMC/*.txt */
public final class PolygonAreaStore {

    private final Plugin plugin;
    private final String fileName;
    private final String areaLabel;
    private int nextIndex = 1;

    public PolygonAreaStore(Plugin plugin, String fileName, String areaLabel) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.areaLabel = areaLabel;
        RootRecordFolders.ensureDir(plugin);
    }

    public Path path() {
        return RootRecordFolders.configFile(plugin, fileName).toPath();
    }

    public SpawnBoundary loadOptional() {
        try {
            return loadOrEmpty();
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not load " + fileName + ": " + ex.getMessage());
            return new SpawnBoundary("world", List.of());
        }
    }

    public SpawnBoundary loadOrEmpty() throws IOException {
        Path file = path();
        if (!Files.isRegularFile(file)) {
            return new SpawnBoundary("world", List.of());
        }
        return SpawnBoundary.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public SpawnBoundary loadOrDefault(String defaultResource) throws IOException {
        Path file = path();
        seedDefaultIfNeeded(file, defaultResource);
        if (!Files.isRegularFile(file)) {
            throw new IOException(fileName + " missing");
        }
        return SpawnBoundary.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    private void seedDefaultIfNeeded(Path file, String defaultResource) throws IOException {
        seedDefaultBoundary(plugin, file, defaultResource, fileName);
    }

    /**
     * Seeds boundary file from jar when missing, empty, or legacy origin ring.
     */
    static void seedDefaultBoundary(Plugin plugin, Path file, String defaultResource, String logLabel)
            throws IOException {
        boolean seed = !Files.isRegularFile(file);
        if (!seed) {
            SpawnBoundary current = SpawnBoundary.parse(Files.readString(file, StandardCharsets.UTF_8));
            seed = current.isEmpty() || isLegacyOriginRing(current);
        }
        if (!seed) {
            return;
        }
        try (InputStream in = plugin.getResource(defaultResource)) {
            if (in == null) {
                return;
            }
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Seeded " + logLabel + " from plugin default (ridge spawn walls).");
        }
    }

    /** Old circular ring near (0,0); new default is ridge platform around X≈-850, Z≈-295. */
    static boolean isLegacyOriginRing(SpawnBoundary boundary) {
        int[] bounds = boundary.horizontalBounds();
        if (bounds == null) {
            return false;
        }
        return bounds[1] > -500;
    }

    public synchronized void beginSession(String worldName, String startedBy) throws IOException {
        nextIndex = 1;
        List<String> header = new ArrayList<>();
        header.add("# RootMC " + areaLabel + " perimeter — waypoint list in click order");
        header.add("# Format: index,world,x,z[,marked_y][,recorded_at]");
        header.add("# Vertical span: basement y=" + BasementZone.DEFAULT_BOTTOM + " .. " + BasementZone.DEFAULT_TOP);
        header.add("session_started=" + Instant.now() + " by=" + startedBy);
        header.add("world=" + worldName);
        header.add("---");
        Files.writeString(path(), String.join(System.lineSeparator(), header) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public synchronized int appendWaypoint(String worldName, int x, int z, int markedY) throws IOException {
        int index = nextIndex++;
        String line = index + "," + worldName + "," + x + "," + z + "," + markedY + "," + Instant.now();
        Files.writeString(path(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return index;
    }

    public void saveBoundary(SpawnBoundary boundary, String savedBy) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# RootMC " + areaLabel + " perimeter");
        lines.add("# Format: index,world,x,z");
        lines.add("updated_at=" + Instant.now() + " by=" + savedBy);
        lines.add("world=" + boundary.worldName());
        lines.add("---");
        int i = 1;
        for (int[] p : boundary.vertices()) {
            lines.add(i + "," + boundary.worldName() + "," + p[0] + "," + p[1]);
            i++;
        }
        Files.writeString(path(), String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }
}
