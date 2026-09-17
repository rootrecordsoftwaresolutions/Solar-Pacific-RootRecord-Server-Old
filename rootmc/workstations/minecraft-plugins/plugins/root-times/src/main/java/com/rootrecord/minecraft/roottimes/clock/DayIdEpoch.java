package com.rootrecord.minecraft.roottimes.clock;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Persists {@code minecraft-day.day-id-base} when set to {@code auto}/{@code now}
 * so local MC day 0 survives restarts.
 */
public final class DayIdEpoch {

    public static final String FILE_NAME = "mc-day-epoch.txt";

    private DayIdEpoch() {}

    /**
     * @param raw config value: blank/{@code 0} = absolute days; {@code auto}/{@code now} =
     *     persist absolute day at first resolve; otherwise a numeric absolute base.
     */
    public static long resolve(Plugin plugin, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || "0".equals(v)) {
            return 0L;
        }
        String lower = v.toLowerCase(Locale.ROOT);
        if ("auto".equals(lower) || "now".equals(lower)) {
            return ensurePersisted(plugin);
        }
        try {
            return Math.max(0L, Long.parseLong(v));
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("Invalid minecraft-day.day-id-base '" + v + "' — using 0.");
            return 0L;
        }
    }

    public static long ensurePersisted(Plugin plugin) {
        Path path = RootRecordFolders.configFile(plugin, FILE_NAME).toPath();
        try {
            if (Files.isRegularFile(path)) {
                String text = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    long stored = Long.parseLong(text.split("\\R", 2)[0].trim());
                    if (stored > 0L) {
                        return stored;
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + FILE_NAME + ": " + ex.getMessage());
        }
        long absolute = McDayClock.absoluteDayId();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    absolute
                            + System.lineSeparator()
                            + "# Absolute McDayClock day id = local day 0. Delete to re-anchor."
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            plugin.getLogger().info(
                    "Claims/local MC day epoch set to absolute day " + absolute + " (local day 0).");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + FILE_NAME + ": " + ex.getMessage());
        }
        return Math.max(0L, absolute);
    }

    /** Delete persisted epoch so {@code auto}/{@code now} re-anchors local day 0 (new world). */
    public static void clearPersisted(Plugin plugin) {
        Path path = RootRecordFolders.configFile(plugin, FILE_NAME).toPath();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not delete " + FILE_NAME + ": " + ex.getMessage());
        }
    }
}
