package com.rootrecord.minecraft.roothaste.data;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

/** Persists longest burn duration (torch or joint theme file). */
public final class JointRecordStore {

    private final Plugin plugin;
    private final String fileName;
    private final File file;
    private long longestMs;
    private String sparkedBy = "";
    private String lastHolder = "";
    private String recordedAt = "";

    public JointRecordStore(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName == null || fileName.isBlank() ? "root-haste-records.yml" : fileName;
        RootRecordFolders.ensureDir(plugin);
        this.file = RootRecordFolders.configFile(plugin, this.fileName);
        load();
    }

    public void load() {
        longestMs = 0L;
        sparkedBy = "";
        lastHolder = "";
        recordedAt = "";
        if (!file.isFile()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        longestMs = Math.max(0L, cfg.getLong("longest.duration-ms", 0L));
        sparkedBy = cfg.getString("longest.sparked-by", "");
        if (sparkedBy == null || sparkedBy.isBlank()) {
            sparkedBy = cfg.getString("longest.lit-by", "");
        }
        lastHolder = cfg.getString("longest.last-holder", "");
        recordedAt = cfg.getString("longest.recorded-at", "");
    }

    public long longestMs() {
        return longestMs;
    }

    public String sparkedBy() {
        return sparkedBy == null ? "" : sparkedBy;
    }

    public String lastHolder() {
        return lastHolder == null ? "" : lastHolder;
    }

    public String recordedAt() {
        return recordedAt == null ? "" : recordedAt;
    }

    /** @return true when this duration sets a new record */
    public boolean tryRecord(long durationMs, String sparkedByName, String lastHolderName) {
        if (durationMs <= 0 || durationMs <= longestMs) {
            return false;
        }
        longestMs = durationMs;
        sparkedBy = sparkedByName == null ? "" : sparkedByName;
        lastHolder = lastHolderName == null ? "" : lastHolderName;
        recordedAt = Instant.now().toString();
        save();
        return true;
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("longest.duration-ms", longestMs);
        cfg.set("longest.duration-seconds", longestMs / 1000L);
        cfg.set("longest.sparked-by", sparkedBy);
        cfg.set("longest.lit-by", sparkedBy);
        cfg.set("longest.last-holder", lastHolder);
        cfg.set("longest.recorded-at", recordedAt);
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save " + fileName + ": " + ex.getMessage());
        }
    }

    public static String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        if (min <= 0) {
            return sec + "s";
        }
        return String.format(Locale.US, "%dm %ds", min, sec);
    }
}
