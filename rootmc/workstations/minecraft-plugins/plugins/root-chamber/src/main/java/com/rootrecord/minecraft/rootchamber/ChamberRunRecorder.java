package com.rootrecord.minecraft.rootchamber;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/** Local + optional MySQL chamber run metrics. */
final class ChamberRunRecorder {

    enum Outcome {
        WIN,
        DEATH,
        DISQUALIFIED,
        DISCONNECT
    }

    record RunRecord(
            UUID playerId,
            String playerName,
            Outcome outcome,
            long durationMs,
            int lavaTotal,
            int lavaCeilingsOpened,
            int lavaFloorsOpened,
            double prizeGold,
            boolean prizePaid) {}

    private final JavaPlugin plugin;
    private final File statsFile;
    private final Path runsLog;
    private final ChamberMysqlStore mysql;

    ChamberRunRecorder(JavaPlugin plugin) {
        this.plugin = plugin;
        RootRecordFolders.ensureDir(plugin);
        statsFile = new File(RootRecordFolders.dir(plugin), "chamber-stats.yml");
        runsLog = RootRecordFolders.dir(plugin).toPath().resolve("chamber-runs.jsonl");
        mysql = new ChamberMysqlStore(plugin);
        mysql.initAsync();
    }

    void record(RunRecord record) {
        if (record == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            writeLocal(record);
            appendJsonl(record);
            mysql.insert(record);
        });
    }

    private void writeLocal(RunRecord record) {
        YamlConfiguration yaml = statsFile.isFile()
                ? YamlConfiguration.loadConfiguration(statsFile)
                : new YamlConfiguration();
        String base = "players." + record.playerId();
        yaml.set(base + ".name", record.playerName());
        yaml.set(base + ".last_run_at", Instant.now().toString());
        yaml.set(base + ".last_outcome", record.outcome().name());
        yaml.set(base + ".last_lava_total", record.lavaTotal());
        yaml.set(base + ".last_lava_ceilings", record.lavaCeilingsOpened());
        yaml.set(base + ".last_lava_floors", record.lavaFloorsOpened());
        yaml.set(base + ".last_duration_ms", record.durationMs());
        increment(yaml, base + ".runs_total");
        switch (record.outcome()) {
            case WIN -> {
                increment(yaml, base + ".wins");
                increment(yaml, "global.wins");
            }
            case DEATH -> {
                increment(yaml, base + ".deaths");
                increment(yaml, "global.deaths");
            }
            case DISQUALIFIED -> {
                increment(yaml, base + ".disqualified");
                increment(yaml, "global.disqualified");
            }
            case DISCONNECT -> {
                increment(yaml, base + ".disconnects");
                increment(yaml, "global.disconnects");
            }
        }
        increment(yaml, "global.runs_total");
        yaml.set("global.updated_at", Instant.now().toString());
        try {
            yaml.save(statsFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("chamber-stats.yml save failed: " + ex.getMessage());
        }
    }

    private static void increment(YamlConfiguration yaml, String path) {
        yaml.set(path, yaml.getLong(path, 0L) + 1L);
    }

    private void appendJsonl(RunRecord record) {
        String line = "{"
                + "\"at\":\"" + Instant.now() + "\","
                + "\"uuid\":\"" + record.playerId() + "\","
                + "\"name\":\"" + escapeJson(record.playerName()) + "\","
                + "\"outcome\":\"" + record.outcome().name() + "\","
                + "\"duration_ms\":" + record.durationMs() + ","
                + "\"lava_total\":" + record.lavaTotal() + ","
                + "\"lava_ceilings_opened\":" + record.lavaCeilingsOpened() + ","
                + "\"lava_floors_opened\":" + record.lavaFloorsOpened() + ","
                + "\"prize_gold\":" + record.prizeGold() + ","
                + "\"prize_paid\":" + record.prizePaid()
                + "}";
        try {
            Files.writeString(runsLog, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("chamber-runs.jsonl append failed: " + ex.getMessage());
        }
    }

    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
