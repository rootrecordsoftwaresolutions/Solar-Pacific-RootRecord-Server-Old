package com.rootrecord.minecraft.rootspawn;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RefinedAreaStore {

    private final Plugin plugin;
    private final Path file;

    public RefinedAreaStore(Plugin plugin) {
        RootRecordFolders.ensureDir(plugin);
        this.plugin = plugin;
        file = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_SPAWN_REFINED_FILE).toPath();
    }

    public SpawnBoundary loadOrDefault() throws IOException {
        Path file = this.file;
        PolygonAreaStore.seedDefaultBoundary(plugin, file, "spawnarea-refined.txt", "spawnarea-refined.txt");
        if (!Files.isRegularFile(file)) {
            throw new IOException("spawnarea-refined.txt missing");
        }
        return SpawnBoundary.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public void save(SpawnBoundary boundary, String savedBy) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# RootMC spawn perimeter — refined safe zone");
        lines.add("# Format: index,world,x,z");
        lines.add("# Vertical span: y=0 .. world max height");
        lines.add("updated_at=" + Instant.now() + " by=" + savedBy);
        lines.add("world=" + boundary.worldName());
        lines.add("---");
        int i = 1;
        for (int[] p : boundary.vertices()) {
            lines.add(i + "," + boundary.worldName() + "," + p[0] + "," + p[1]);
            i++;
        }
        Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    public Path file() {
        return file;
    }
}
