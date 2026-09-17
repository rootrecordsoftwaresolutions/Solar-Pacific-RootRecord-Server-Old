package com.rootrecord.minecraft.rootspawn;

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

/** Appends perimeter waypoints to plugins/RootMC/spawnarea.txt */
public final class SpawnAreaStore {

    private final Path file;
    private int nextIndex = 1;

    public SpawnAreaStore(Plugin plugin) {
        RootRecordFolders.ensureDir(plugin);
        file = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_SPAWN_AREA_FILE).toPath();
    }

    public synchronized void beginSession(String worldName, String startedBy) throws IOException {
        nextIndex = 1;
        List<String> header = List.of(
                "# RootMC spawn perimeter — waypoint list in click order",
                "# Format: index,world,x,z,marked_y,recorded_at",
                "# Vertical span for the finished area: y=0 through world max height (marked_y is reference only)",
                "# Temporary mapper from root-spawn — remove plugin stage after import",
                "session_started=" + Instant.now() + " by=" + startedBy,
                "world=" + worldName,
                "---");
        Files.writeString(file, String.join(System.lineSeparator(), header) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public synchronized int appendWaypoint(String worldName, int x, int z, int markedY) throws IOException {
        int index = nextIndex++;
        String line = index + "," + worldName + "," + x + "," + z + "," + markedY + "," + Instant.now();
        Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return index;
    }

    public Path file() {
        return file;
    }

    public static List<int[]> readWaypoints(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        List<int[]> points = new ArrayList<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("session_") || line.startsWith("world=")
                    || line.equals("---")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 4) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[2].trim());
                int z = Integer.parseInt(parts[3].trim());
                points.add(new int[] {x, z});
            } catch (NumberFormatException ignored) {
                // skip malformed row
            }
        }
        return points;
    }
}
