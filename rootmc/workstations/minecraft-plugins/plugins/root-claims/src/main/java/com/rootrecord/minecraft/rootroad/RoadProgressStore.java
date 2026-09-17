package com.rootrecord.minecraft.rootroad;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/** Persists per-player road progress under plugins/RootMC/root-road-progress.yml. */
public final class RoadProgressStore {

    private static final String DATA_FILE = "root-road-progress.yml";

    private final RootRoadPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public RoadProgressStore(RootRoadPlugin plugin) {
        this.plugin = plugin;
        this.file = RootRecordFolders.configFile(plugin.host(), DATA_FILE);
    }

    public void load() {
        RootRecordFolders.ensureDir(plugin.host());
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (yaml == null) {
            return;
        }
        try {
            RootRecordFolders.ensureDir(plugin.host());
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + DATA_FILE, ex);
        }
    }

    public RoadStep step(UUID playerId) {
        if (playerId == null || yaml == null) {
            return RoadStep.WELCOME;
        }
        String raw = yaml.getString(path(playerId) + ".step", RoadStep.WELCOME.name());
        try {
            return RoadStep.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return RoadStep.WELCOME;
        }
    }

    public void setStep(UUID playerId, RoadStep step) {
        if (playerId == null || step == null || yaml == null) {
            return;
        }
        String base = path(playerId);
        yaml.set(base + ".step", step.name());
        yaml.set(base + ".updated-at", System.currentTimeMillis());
        if (yaml.getLong(base + ".started-at", 0L) <= 0L) {
            yaml.set(base + ".started-at", System.currentTimeMillis());
        }
        if (step.isComplete()) {
            yaml.set(base + ".completed-at", System.currentTimeMillis());
        }
        save();
    }

    public void reset(UUID playerId) {
        if (playerId == null || yaml == null) {
            return;
        }
        yaml.set(path(playerId), null);
        save();
    }

    public void appendJoinLog(UUID playerId, String playerName, String detail) {
        if (playerId == null || yaml == null) {
            return;
        }
        String base = path(playerId);
        long id = Math.max(1L, yaml.getLong(base + ".meta.next-join-id", 1L));
        String row = base + ".joins." + id;
        yaml.set(row + ".at", System.currentTimeMillis());
        yaml.set(row + ".name", playerName == null ? "" : playerName);
        yaml.set(row + ".detail", detail == null ? "" : detail);
        yaml.set(row + ".step", step(playerId).name());
        yaml.set(base + ".meta.next-join-id", id + 1);
        yaml.set(base + ".meta.last-join-at", System.currentTimeMillis());
        yaml.set(base + ".meta.last-join-name", playerName == null ? "" : playerName);
        save();
        plugin.getLogger().info("[first-join-road] " + playerName + " (" + playerId + ") " + detail
                + " step=" + step(playerId).name());
    }

    public void appendEventLog(UUID playerId, String playerName, String event, String detail) {
        if (playerId == null || yaml == null) {
            return;
        }
        String base = path(playerId);
        long id = Math.max(1L, yaml.getLong(base + ".meta.next-event-id", 1L));
        String row = base + ".events." + id;
        yaml.set(row + ".at", System.currentTimeMillis());
        yaml.set(row + ".name", playerName == null ? "" : playerName);
        yaml.set(row + ".event", event == null ? "" : event);
        yaml.set(row + ".detail", detail == null ? "" : detail);
        yaml.set(row + ".step", step(playerId).name());
        yaml.set(base + ".meta.next-event-id", id + 1);
        save();
        plugin.getLogger().info("[road-event] " + playerName + " event=" + event + " " + detail
                + " step=" + step(playerId).name());
    }

    private static String path(UUID playerId) {
        return "players." + playerId;
    }
}
