package com.rootrecord.minecraft.rootspawn;

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

/** Load/save chamber lava ceiling/floor pairs in plugins/RootMC/lava.txt */
public final class LavaSpotStore {

    private final Plugin plugin;
    private final Map<UUID, int[]> pendingCeiling = new ConcurrentHashMap<>();
    private int nextSpotIndex = 1;

    public LavaSpotStore(Plugin plugin) {
        this.plugin = plugin;
        RootRecordFolders.ensureDir(plugin);
    }

    public Path path() {
        return RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_SPAWN_LAVA_FILE).toPath();
    }

    public synchronized void beginSession(String worldName, String startedBy) throws IOException {
        nextSpotIndex = 1;
        pendingCeiling.clear();
        List<String> header = new ArrayList<>();
        header.add("# RootMC chamber lava drops — ceiling/floor pairs in click order");
        header.add("# Format: spot,world,ceil_x,ceil_y,ceil_z,floor_x,floor_y,floor_z[,recorded_at]");
        header.add("# Click ceiling first, then floor, for each lava rain spot.");
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

    public List<LavaSpot> loadSpots() {
        try {
            Path file = path();
            if (!java.nio.file.Files.isRegularFile(file)) {
                plugin.getLogger().warning("lava.txt missing at " + file + " — no chamber lava drops.");
                return List.of();
            }
            List<LavaSpot> spots = parse(java.nio.file.Files.readString(file, StandardCharsets.UTF_8));
            plugin.getLogger().info("Loaded " + spots.size() + " chamber lava spot(s) from " + file);
            return spots;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not load lava.txt: " + ex.getMessage());
            return List.of();
        }
    }

    static List<LavaSpot> parse(String text) {
        List<LavaSpot> spots = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return spots;
        }
        String worldFallback = "world";
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("world=")) {
                worldFallback = line.substring("world=".length()).trim();
                continue;
            }
            if (line.startsWith("---") || line.contains("session_started=")) {
                continue;
            }
            String[] parts = line.split(",", 9);
            if (parts.length < 8) {
                continue;
            }
            try {
                String world = parts[1];
                spots.add(new LavaSpot(
                        world.isEmpty() ? worldFallback : world,
                        Integer.parseInt(parts[2].trim()),
                        Integer.parseInt(parts[3].trim()),
                        Integer.parseInt(parts[4].trim()),
                        Integer.parseInt(parts[5].trim()),
                        Integer.parseInt(parts[6].trim()),
                        Integer.parseInt(parts[7].trim())));
            } catch (NumberFormatException ignored) {
                // skip bad row
            }
        }
        return spots;
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
