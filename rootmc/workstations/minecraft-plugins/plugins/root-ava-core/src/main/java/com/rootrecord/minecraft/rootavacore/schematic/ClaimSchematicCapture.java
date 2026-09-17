package com.rootrecord.minecraft.rootavacore.schematic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Capture blocks inside a circular Root-Claims AABB (circle clipped to bounding box).
 */
public final class ClaimSchematicCapture {

    private ClaimSchematicCapture() {}

    public record CaptureResult(
            String world,
            int claimX,
            int claimY,
            int claimZ,
            int claimRadius,
            int width,
            int height,
            int length,
            int originX,
            int originY,
            int originZ,
            int offsetX,
            int offsetY,
            int offsetZ,
            int dataVersion,
            List<String> palette,
            int[] blockIndices,
            int nonAir,
            Map<String, Integer> materialCounts) {}

    /**
     * @param yBelow blocks below claim Y (clamped to world min)
     * @param yAbove blocks above claim Y (clamped to world max)
     * @param extraRadius blocks past claim radius (territory vision); 0 = claim only
     */
    public static CaptureResult capture(
            Object claim,
            int yBelow,
            int yAbove,
            int extraRadius) {
        World world = claimWorld(claim);
        if (world == null) return null;
        int cx = claimX(claim);
        int cy = claimY(claim);
        int cz = claimZ(claim);
        int radius = claimRadius(claim) + Math.max(0, extraRadius);
        // Soft cap so we don't freeze the server on huge buffers
        radius = Math.min(radius, 48);
        int minX = cx - radius;
        int maxX = cx + radius;
        int minZ = cz - radius;
        int maxZ = cz + radius;
        int minY = Math.max(world.getMinHeight(), cy - Math.max(0, yBelow));
        int maxY = Math.min(world.getMaxHeight() - 1, cy + Math.max(0, yAbove));
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        int height = maxY - minY + 1;
        if (width <= 0 || length <= 0 || height <= 0) return null;

        Map<String, Integer> paletteMap = new LinkedHashMap<>();
        List<String> paletteOrder = new ArrayList<>();
        int[] blockIndices = new int[width * height * length];
        Map<String, Integer> counts = new HashMap<>();
        int nonAir = 0;
        int index = 0;
        int r2 = radius * radius;

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    String state;
                    int dx = x - cx;
                    int dz = z - cz;
                    if (dx * dx + dz * dz > r2) {
                        state = Material.AIR.createBlockData().getAsString();
                    } else {
                        Block block = world.getBlockAt(x, y, z);
                        state = block.getBlockData().getAsString();
                        if (!block.getType().isAir()) {
                            nonAir++;
                            String mat = block.getType().getKey().toString();
                            counts.merge(mat, 1, Integer::sum);
                        }
                    }
                    blockIndices[index++] = paletteIndex(state, paletteOrder, paletteMap);
                }
            }
        }

        int dataVersion = 3955;
        try {
            dataVersion = Bukkit.getUnsafe().getDataVersion();
        } catch (Throwable ignored) {
        }

        return new CaptureResult(
                world.getName(),
                cx,
                cy,
                cz,
                radius,
                width,
                height,
                length,
                minX,
                minY,
                minZ,
                cx - minX,
                cy - minY,
                cz - minZ,
                dataVersion,
                paletteOrder,
                blockIndices,
                nonAir,
                counts);
    }

    public static List<Map.Entry<String, Integer>> topMaterials(CaptureResult cap, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(cap.materialCounts().entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        if (entries.size() > limit) {
            return entries.subList(0, limit);
        }
        return entries;
    }

    private static int paletteIndex(String state, List<String> paletteOrder, Map<String, Integer> paletteMap) {
        Integer existing = paletteMap.get(state);
        if (existing != null) {
            return existing;
        }
        int index = paletteOrder.size();
        paletteOrder.add(state);
        paletteMap.put(state, index);
        return index;
    }

    static World claimWorld(Object claim) {
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            String world = String.valueOf(key.getClass().getMethod("world").invoke(key));
            return Bukkit.getWorld(world);
        } catch (Throwable t) {
            return null;
        }
    }

    static int claimX(Object claim) {
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            return ((Number) key.getClass().getMethod("x").invoke(key)).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    static int claimY(Object claim) {
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            return ((Number) key.getClass().getMethod("y").invoke(key)).intValue();
        } catch (Throwable t) {
            return 64;
        }
    }

    static int claimZ(Object claim) {
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            return ((Number) key.getClass().getMethod("z").invoke(key)).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    static int claimRadius(Object claim) {
        try {
            return ((Number) claim.getClass().getMethod("radiusBlocks").invoke(claim)).intValue();
        } catch (Throwable t) {
            return 16;
        }
    }

    public static Location originLocation(CaptureResult cap) {
        World w = Bukkit.getWorld(cap.world());
        if (w == null) return null;
        return new Location(w, cap.originX(), cap.originY(), cap.originZ());
    }

    public static String safeFileStem(CaptureResult cap) {
        return String.format(
                Locale.ROOT,
                "%s_%d_%d_%d_r%d",
                cap.world().replaceAll("[^A-Za-z0-9_-]", "_"),
                cap.claimX(),
                cap.claimY(),
                cap.claimZ(),
                cap.claimRadius());
    }
}
