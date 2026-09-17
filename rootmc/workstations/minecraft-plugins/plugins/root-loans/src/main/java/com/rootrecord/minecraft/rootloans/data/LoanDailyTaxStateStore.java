package com.rootrecord.minecraft.rootloans.data;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class LoanDailyTaxStateStore {

    private static final String STATE_FILE = "root-loans-tax-state.yml";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final File file;
    private YamlConfiguration yaml;

    public LoanDailyTaxStateStore(JavaPlugin plugin) {
        this.file = new File(RootRecordFolders.dir(plugin), STATE_FILE);
    }

    public void load() {
        if (!file.exists()) {
            yaml = new YamlConfiguration();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public LocalDate lastRunDate() {
        if (yaml == null) {
            return null;
        }
        String raw = yaml.getString("last-run-date");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw, DATE);
        } catch (Exception ex) {
            return null;
        }
    }

    public void markRun(LocalDate date) {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }
        yaml.set("last-run-date", DATE.format(date));
        try {
            yaml.save(file);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
