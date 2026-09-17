package com.rootrecord.minecraft.rootannouncer;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public record AnnouncerConfig(
        boolean enabled,
        long intervalSeconds,
        boolean randomOrder,
        int requireMinPlayers,
        String muteSuffix,
        List<String> lines,
        boolean cloudSyncEnabled,
        long cloudSyncIntervalMinutes,
        boolean seasonLinesFirst) {

    public static AnnouncerConfig from(FileConfiguration yaml) {
        if (yaml == null) {
            return defaults();
        }
        List<String> lines = new ArrayList<>();
        for (String line : yaml.getStringList("messages.lines")) {
            if (line != null && !line.isBlank()) {
                lines.add(line);
            }
        }
        return new AnnouncerConfig(
                yaml.getBoolean("enabled", true),
                Math.max(15L, yaml.getLong("interval-seconds", 300)),
                yaml.getBoolean("random-order", true),
                Math.max(0, yaml.getInt("require-min-players", 1)),
                yaml.getString("messages.mute-suffix", ""),
                List.copyOf(lines),
                yaml.getBoolean("cloud.sync-enabled", true),
                Math.max(1L, yaml.getLong("cloud.sync-interval-minutes", 5)),
                yaml.getBoolean("cloud.season-lines-first", true));
    }

    public static AnnouncerConfig defaults() {
        return new AnnouncerConfig(
                true,
                300L,
                true,
                1,
                "",
                List.of("&7Welcome to &bRootMC&7!"),
                true,
                5L,
                true);
    }
}
