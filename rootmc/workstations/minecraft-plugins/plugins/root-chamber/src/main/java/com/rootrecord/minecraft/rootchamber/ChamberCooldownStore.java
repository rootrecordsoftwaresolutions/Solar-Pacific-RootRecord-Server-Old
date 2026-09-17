package com.rootrecord.minecraft.rootchamber;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Per-player 24h chamber minigame cooldown (plugins/RootMC/chamber-cooldowns.yml). */
final class ChamberCooldownStore {

    private final Plugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    ChamberCooldownStore(Plugin plugin) {
        RootRecordFolders.ensureDir(plugin);
        this.plugin = plugin;
        file = new File(RootRecordFolders.dir(plugin), "chamber-cooldowns.yml");
        reload();
    }

    void reload() {
        if (!file.isFile()) {
            yaml = new YamlConfiguration();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    boolean isOnCooldown(UUID playerId, long cooldownMs) {
        long until = yaml.getLong(path(playerId), 0L);
        return until > Instant.now().toEpochMilli();
    }

    long cooldownRemainingMs(UUID playerId) {
        long until = yaml.getLong(path(playerId), 0L);
        return Math.max(0L, until - Instant.now().toEpochMilli());
    }

    void setCooldown(UUID playerId, long cooldownMs) {
        yaml.set(path(playerId), Instant.now().toEpochMilli() + cooldownMs);
        saveQuietly();
    }

    private static String path(UUID id) {
        return "cooldowns." + id;
    }

    private void saveQuietly() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("chamber-cooldowns save failed: " + ex.getMessage());
        }
    }
}
