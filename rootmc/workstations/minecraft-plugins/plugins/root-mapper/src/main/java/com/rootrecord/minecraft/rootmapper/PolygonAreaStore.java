package com.rootrecord.minecraft.rootmapper;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Load/save polygon waypoints under plugins/RootMC/. */
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

    public String fileName() {
        return fileName;
    }

    public Path path() {
        Path file = RootRecordFolders.configFile(plugin, fileName).toPath();
        Path parent = file.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not create mapper directory: " + ex.getMessage());
            }
        }
        return file;
    }

    public PolygonBoundary loadOptional() {
        try {
            return loadOrEmpty();
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not load " + fileName + ": " + ex.getMessage());
            return new PolygonBoundary("world", List.of());
        }
    }

    public PolygonBoundary loadOrEmpty() throws IOException {
        Path file = path();
        if (!Files.isRegularFile(file)) {
            return new PolygonBoundary("world", List.of());
        }
        return PolygonBoundary.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public synchronized void beginSession(String worldName, String startedBy) throws IOException {
        nextIndex = 1;
        List<String> header = new ArrayList<>();
        header.add("# RootMC " + areaLabel + " perimeter — waypoint list in click order");
        header.add("# Format: index,world,x,z[,marked_y][,recorded_at]");
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

    public void saveBoundary(PolygonBoundary boundary, String savedBy) throws IOException {
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
