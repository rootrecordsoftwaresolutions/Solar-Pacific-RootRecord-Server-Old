package com.rootrecord.minecraft.rootiteminfo.census;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Running material totals for the scanned world snapshot. */
public final class ItemCensusStore {

    private final Path file;
    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private volatile long scannedAtEpochMs;
    private volatile String scanNote = "not scanned yet";
    private Logger logger;

    public ItemCensusStore(Path file) {
        this.file = file;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void load() {
        counts.clear();
        if (!Files.isRegularFile(file)) {
            scannedAtEpochMs = 0L;
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        scannedAtEpochMs = yaml.getLong("scanned-at-epoch-ms", 0L);
        scanNote = yaml.getString("scan-note", "loaded from disk");
        var section = yaml.getConfigurationSection("counts");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                long value = section.getLong(key, 0L);
                if (value > 0 && key != null && !key.isBlank()) {
                    counts.put(key.toLowerCase(), new AtomicLong(value));
                }
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("scanned-at-epoch-ms", scannedAtEpochMs);
            yaml.set("scan-note", scanNote);
            Map<String, Long> snap = snapshot();
            for (Map.Entry<String, Long> e : snap.entrySet()) {
                yaml.set("counts." + e.getKey(), e.getValue());
            }
            yaml.save(file.toFile());
        } catch (IOException ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to save item census: " + ex.getMessage(), ex);
            }
        }
    }

    public void replaceAll(Map<String, Long> next, String note) {
        counts.clear();
        for (Map.Entry<String, Long> e : next.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            counts.put(e.getKey().toLowerCase(), new AtomicLong(e.getValue()));
        }
        scannedAtEpochMs = System.currentTimeMillis();
        scanNote = note == null ? "" : note;
        save();
    }

    public long count(String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            return 0L;
        }
        AtomicLong value = counts.get(materialKey.toLowerCase());
        return value == null ? 0L : Math.max(0L, value.get());
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> out = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .forEach(e -> {
                    long v = e.getValue().get();
                    if (v > 0) {
                        out.put(e.getKey(), v);
                    }
                });
        return Collections.unmodifiableMap(out);
    }

    public long scannedAtEpochMs() {
        return scannedAtEpochMs;
    }

    public String scanNote() {
        return scanNote;
    }

    public int distinctItems() {
        return (int) counts.values().stream().filter(v -> v.get() > 0).count();
    }
}
