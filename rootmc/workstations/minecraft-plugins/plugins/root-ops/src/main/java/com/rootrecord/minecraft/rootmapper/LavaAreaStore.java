package com.rootrecord.minecraft.rootmapper;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ceiling/floor lava pairs for a named mapper area. */
public final class LavaAreaStore {

    private final Plugin plugin;
    private final String fileName;
    private final String areaLabel;
    private final Map<UUID, int[]> pendingCeiling = new ConcurrentHashMap<>();
    private int nextSpotIndex = 1;

    public LavaAreaStore(Plugin plugin, String fileName, String areaLabel) {
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

    public synchronized void beginSession(String worldName, String startedBy) throws IOException {
        nextSpotIndex = 1;
        pendingCeiling.clear();
        List<String> header = new ArrayList<>();
        header.add("# RootMC " + areaLabel + " lava drops — ceiling/floor pairs");
        header.add("# Format: spot,world,ceil_x,ceil_y,ceil_z,floor_x,floor_y,floor_z[,recorded_at]");
        header.add("session_started=" + Instant.now() + " by=" + startedBy);
        header.add("world=" + worldName);
        header.add("---");
        Files.writeString(path(), String.join(System.lineSeparator(), header) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public synchronized LavaClickResult recordClick(UUID playerId, Block block) throws IOException {
        if (block == null || block.getWorld() == null) {
            throw new IOException("invalid block");
        }
        int[] pending = pendingCeiling.get(playerId);
        if (pending == null) {
            pendingCeiling.put(playerId, new int[] {
                    block.getX(), block.getY(), block.getZ()
            });
            return new LavaClickResult(LavaClickResult.Kind.CEILING, 0, block.getX(), block.getY(), block.getZ());
        }
        pendingCeiling.remove(playerId);
        int spot = nextSpotIndex++;
        String world = block.getWorld().getName();
        String line = spot + "," + world + ","
                + pending[0] + "," + pending[1] + "," + pending[2] + ","
                + block.getX() + "," + block.getY() + "," + block.getZ() + ","
                + Instant.now();
        Files.writeString(path(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return new LavaClickResult(LavaClickResult.Kind.SPOT_SAVED, spot,
                pending[0], pending[1], pending[2],
                block.getX(), block.getY(), block.getZ());
    }

    public void clearPending(UUID playerId) {
        pendingCeiling.remove(playerId);
    }

    record LavaClickResult(
            Kind kind,
            int spotIndex,
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2) {

        LavaClickResult(Kind kind, int spotIndex, int x, int y, int z) {
            this(kind, spotIndex, x, y, z, 0, 0, 0);
        }

        enum Kind {
            CEILING,
            SPOT_SAVED
        }
    }
}
