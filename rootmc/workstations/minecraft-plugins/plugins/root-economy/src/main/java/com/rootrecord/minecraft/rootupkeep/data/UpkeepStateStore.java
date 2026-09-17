package com.rootrecord.minecraft.rootupkeep.data;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

public final class UpkeepStateStore {

    private static final String STATE_FILE = "root-upkeep-state.yml";
    private final File file;
    private YamlConfiguration yaml;

    public UpkeepStateStore(JavaPlugin plugin) {
        this.file = new File(RootRecordFolders.dir(plugin), STATE_FILE);
    }

    public void load() {
        if (!file.exists()) {
            yaml = new YamlConfiguration();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        // Migrate off Minecraft-day cadence: treat today as already run so we do not double-tax.
        if (!yaml.contains("last-run-epoch-day") && yaml.contains("last-run-mc-day-id")) {
            yaml.set("last-run-epoch-day", LocalDate.now(UpkeepConfig.HST).toEpochDay());
            yaml.set("last-run-mc-day-id", null);
            saveQuietly();
        }
    }

    public long lastRunEpochDay() {
        return yaml == null ? -1L : yaml.getLong("last-run-epoch-day", -1L);
    }

    public void markRun(long epochDay) {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        yaml.set("last-run-epoch-day", epochDay);
        yaml.set("last-run-mc-day-id", null);
        saveQuietly();
    }

    public long lastTownTaxMcDayId() {
        return yaml == null ? -1L : yaml.getLong("last-town-tax-mc-day-id", -1L);
    }

    public void markTownTaxMcDay(long mcDayId) {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        yaml.set("last-town-tax-mc-day-id", mcDayId);
        saveQuietly();
    }

    private void saveQuietly() {
        try {
            yaml.save(file);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
