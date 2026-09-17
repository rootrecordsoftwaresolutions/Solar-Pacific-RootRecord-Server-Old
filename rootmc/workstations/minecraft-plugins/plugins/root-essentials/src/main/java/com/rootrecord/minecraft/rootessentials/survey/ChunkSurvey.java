package com.rootrecord.minecraft.rootessentials.survey;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** One-shot chunk survey report for /survey. */
public final class ChunkSurvey {

    private ChunkSurvey() {}

    public static List<String> reportLines(Location playerLoc) {
        List<String> lines = new ArrayList<>();
        if (playerLoc.getWorld() == null) {
            lines.add("&cCould not read world.");
            return lines;
        }
        World world = playerLoc.getWorld();
        Chunk chunk = playerLoc.getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        int minBlockX = chunkX << 4;
        int minBlockZ = chunkZ << 4;
        int maxBlockX = minBlockX + 15;
        int maxBlockZ = minBlockZ + 15;

        boolean slime = isSlimeChunk(world.getSeed(), chunkX, chunkZ);
        Biome feetBiome = playerLoc.getBlock().getBiome();
        Location center = new Location(world, minBlockX + 8.5, playerLoc.getY(), minBlockZ + 8.5);
        Biome centerBiome = center.getBlock().getBiome();

        lines.add("&6&l━━ Chunk Survey Report ━━");
        lines.add("&7Hire a surveyor to survey a chunk.");
        lines.add("&8Shown once — &fscreenshot with F2 &8recommended.");
        lines.add("&8—");
        lines.add("&7World: &f" + world.getName() + " &7(&f" + formatEnvironment(world) + "&7)");
        lines.add("&7Seed: &f" + world.getSeed());
        lines.add("&7Chunk: &f" + chunkX + "&7, &f" + chunkZ);
        lines.add("&7Block region: &f" + minBlockX + ".." + maxBlockX + " &7× &f" + minBlockZ + ".." + maxBlockZ);
        lines.add("&7Your position: &f"
                + playerLoc.getBlockX() + ", " + playerLoc.getBlockY() + ", " + playerLoc.getBlockZ());
        lines.add(slime ? "&aSlime chunk: &fYES &7(slimes can spawn below Y40)" : "&7Slime chunk: &fNO");
        lines.add("&7Biome (feet): &f" + formatBiome(feetBiome));
        lines.add("&7Biome (center): &f" + formatBiome(centerBiome));
        lines.add("&7Surface Y (chunk center): &f" + world.getHighestBlockYAt(minBlockX + 8, minBlockZ + 8));
        lines.add("&7Light (feet): &f" + playerLoc.getBlock().getLightLevel());
        lines.add("&7Inhabited time: &f" + formatDuration(chunk.getInhabitedTime() / 20L));
        lines.add("&7Chunk loaded: &f" + (chunk.isLoaded() ? "yes" : "no")
                + " &7| force-loaded: &f" + (chunk.isForceLoaded() ? "yes" : "no"));
        lines.add("&7Entities: &f" + formatEntityCounts(chunk));
        lines.add("&7Block entities: &f" + chunk.getTileEntities().length);
        appendBiomeVariety(lines, chunk);
        appendStructures(lines, chunk);
        lines.add("&8— &7End of survey");
        return lines;
    }

    /** Vanilla slime-chunk roll (same as Minecraft overworld). */
    static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
        Random random = new Random(
                worldSeed
                        + (long) chunkX * chunkX * 4987142L
                        + (long) chunkX * 5947611L
                        + (long) chunkZ * chunkZ * 4392871L
                        + (long) chunkZ * 389711L
                        ^ 987234911L);
        return random.nextInt(10) == 0;
    }

    private static void appendBiomeVariety(List<String> lines, Chunk chunk) {
        Set<String> biomes = new LinkedHashSet<>();
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int dx = 0; dx < 16; dx += 4) {
            for (int dz = 0; dz < 16; dz += 4) {
                Biome biome = world.getBiome(baseX + dx, 64, baseZ + dz);
                biomes.add(formatBiome(biome));
                if (biomes.size() >= 8) {
                    break;
                }
            }
        }
        if (!biomes.isEmpty()) {
            lines.add("&7Biomes sampled: &f" + String.join("&7, &f", biomes));
        }
    }

    private static void appendStructures(List<String> lines, Chunk chunk) {
        try {
            var method = chunk.getClass().getMethod("getStructures");
            Object raw = method.invoke(chunk);
            if (!(raw instanceof Iterable<?> iterable)) {
                lines.add("&7Structures: &fnot available");
                return;
            }
            List<String> names = new ArrayList<>();
            for (Object structure : iterable) {
                String label = structureLabel(structure);
                if (label != null && !label.isBlank()) {
                    names.add(label);
                }
            }
            if (names.isEmpty()) {
                lines.add("&7Structures: &fnone registered in this chunk");
            } else {
                lines.add("&7Structures: &f" + String.join("&7, &f", names));
            }
        } catch (ReflectiveOperationException ex) {
            lines.add("&7Structures: &fnot available on this server build");
        }
    }

    private static String structureLabel(Object structure) {
        if (structure == null) {
            return null;
        }
        try {
            Object type = structure.getClass().getMethod("getStructure").invoke(structure);
            if (type != null) {
                Object key = type.getClass().getMethod("getKey").invoke(type);
                if (key != null) {
                    return String.valueOf(key);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return structure.toString();
    }

    private static String formatEntityCounts(Chunk chunk) {
        int total = 0;
        int monsters = 0;
        int creatures = 0;
        int ambient = 0;
        int other = 0;
        for (Entity entity : chunk.getEntities()) {
            total++;
            EntityType type = entity.getType();
            if (entity instanceof Monster) {
                monsters++;
            } else if (type.isAlive() && type.isSpawnable()) {
                creatures++;
            } else if (type == EntityType.BAT || type == EntityType.COD || type == EntityType.SALMON
                    || type == EntityType.PUFFERFISH || type == EntityType.TROPICAL_FISH) {
                ambient++;
            } else {
                other++;
            }
        }
        return total + " &7(&fhostile " + monsters + "&7, &fpassive " + creatures
                + "&7, &fambient " + ambient + "&7, &fother " + other + "&7)";
    }

    private static String formatBiome(Biome biome) {
        if (biome == null) {
            return "unknown";
        }
        return biome.getKey().getKey().replace('_', ' ');
    }

    private static String formatEnvironment(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> world.getEnvironment().name().toLowerCase(Locale.ROOT);
        };
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + secs + "s";
        }
        return minutes + "m " + secs + "s";
    }
}
