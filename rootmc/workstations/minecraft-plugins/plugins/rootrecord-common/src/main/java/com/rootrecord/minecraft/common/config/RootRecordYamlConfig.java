package com.rootrecord.minecraft.common.config;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Loads a named YAML file from {@code plugins/RootMC/}. Each plugin uses its own file
 * (e.g. {@code rootstat.yml}) instead of a separate plugin data folder.
 */
public final class RootRecordYamlConfig {

    private final JavaPlugin plugin;
    private final String fileName;
    private final String defaultResource;
    private FileConfiguration configuration;

    public RootRecordYamlConfig(JavaPlugin plugin, String fileName, String defaultResource) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.defaultResource = defaultResource;
    }

    public FileConfiguration config() {
        if (configuration == null) {
            load();
        }
        return configuration;
    }

    public File file() {
        return RootRecordFolders.configFile(plugin, fileName);
    }

    public void load() {
        RootRecordFolders.ensureDir(plugin);
        File file = file();
        if (!file.isFile()) {
            copyDefaultFromJar(file);
        }
        configuration = YamlConfiguration.loadConfiguration(file);
        if (mergeMissingFromJar()) {
            save();
        }
    }

    public void reload() {
        configuration = null;
        load();
    }

    public void save() {
        try {
            config().save(file());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + fileName, ex);
        }
    }

    private void copyDefaultFromJar(File target) {
        try (InputStream in = plugin.getResource(defaultResource)) {
            if (in == null) {
                plugin.getLogger().warning("Missing default config resource: " + defaultResource);
                return;
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write default " + fileName, ex);
        }
    }

    /** Adds keys from the jar default without overwriting operator edits. */
    private boolean mergeMissingFromJar() {
        try (InputStream in = plugin.getResource(defaultResource)) {
            if (in == null) {
                return false;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            FileConfiguration cfg = config();
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !cfg.contains(key)) {
                    cfg.set(key, defaults.get(key));
                    changed = true;
                }
            }
            return changed;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not merge defaults for " + fileName, ex);
            return false;
        }
    }
}
