package com.rootrecord.minecraft.common.config;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/** Shared RootRecord cloud credentials — one file for all plugins. */
public final class RootRecordCloudConfig {

    public static final String FILE_NAME = "cloud.yml";

    private RootRecordCloudConfig() {}

    public record CloudSettings(String apiBase, String serverId, String serverSecret) {
        public boolean hasServerCredentials() {
            return serverId != null && !serverId.isBlank() && serverSecret != null && !serverSecret.isBlank();
        }
    }

    /**
     * Resolves cloud settings for a plugin config. Precedence (first non-empty wins for each field):
     * plugin yaml {@code cloud.*} → {@code plugins/RootMC/cloud.yml} → legacy {@code plugins/RootStat/config.yml}.
     */
    public static CloudSettings resolve(JavaPlugin plugin, FileConfiguration pluginConfig) {
        CloudSettings fromPlugin = fromSection(pluginConfig);
        CloudSettings fromShared = fromFile(RootRecordFolders.configFile(plugin, FILE_NAME));
        CloudSettings fromLegacy = fromFile(legacyRootStatConfig(plugin));

        return new CloudSettings(
                firstNonBlank(
                        fromPlugin.apiBase(),
                        fromShared.apiBase(),
                        fromLegacy.apiBase(),
                        RootMcApiBases.defaultBase()),
                firstNonBlank(fromPlugin.serverId(), fromShared.serverId(), fromLegacy.serverId()),
                firstNonBlank(fromPlugin.serverSecret(), fromShared.serverSecret(), fromLegacy.serverSecret()));
    }

    /** Ensures {@code plugins/RootMC/cloud.yml} exists (from jar default). */
    public static void ensureDefaults(JavaPlugin plugin) {
        RootRecordFolders.ensureDir(plugin);
        File file = RootRecordFolders.configFile(plugin, FILE_NAME);
        if (file.isFile()) {
            return;
        }
        try (InputStream in = plugin.getResource(FILE_NAME)) {
            if (in == null) {
                plugin.getLogger().warning("Missing bundled " + FILE_NAME + " default.");
                return;
            }
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write default " + FILE_NAME, ex);
        }
    }

    private static CloudSettings fromSection(FileConfiguration cfg) {
        if (cfg == null) {
            return empty();
        }
        return new CloudSettings(
                trimSlash(cfg.getString("cloud.api-base")),
                nullToEmpty(cfg.getString("cloud.server-id")),
                nullToEmpty(cfg.getString("cloud.server-secret")));
    }

    private static CloudSettings fromFile(File file) {
        if (!file.isFile()) {
            return empty();
        }
        return fromSection(YamlConfiguration.loadConfiguration(file));
    }

    private static File legacyRootStatConfig(JavaPlugin plugin) {
        return new File(RootRecordFolders.pluginsDir(plugin), "RootStat" + File.separator + "config.yml");
    }

    private static CloudSettings empty() {
        return new CloudSettings("", "", "");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }
}
