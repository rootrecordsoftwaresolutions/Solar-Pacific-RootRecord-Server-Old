package com.rootrecord.minecraft.common.connection;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Idempotent bootstrap for the shared {@code plugins/RootMC/} unit:
 * {@code database.yml}, {@code cloud.yml}, and connection meta.
 * Never overwrites non-blank secrets. Safe for feature plugins when Root-Core is absent.
 */
public final class RootMcCoreConnection {

    /** Bumped when merge defaults or repair contract changes. */
    public static final int CORE_CONNECTION_VERSION = 1;

    private static final Object LOCK = new Object();

    private static final Set<String> PROTECTED_SECRET_KEYS = Set.of(
            "database.password",
            "cloud.server-secret",
            "discord.bot-token",
            "discord-chat.bot-token");

    private RootMcCoreConnection() {}

    public record RepairResult(boolean databaseOk, boolean cloudOk, List<String> warnings) {
        public boolean ok() {
            return databaseOk && cloudOk;
        }
    }

    /**
     * Ensures the RootMC folder and shared connection files exist; merges missing keys only.
     * Thread-safe enough for {@code onEnable}.
     */
    public static RepairResult ensureAndRepair(JavaPlugin plugin) {
        synchronized (LOCK) {
            List<String> warnings = new ArrayList<>();
            RootRecordFolders.ensureDir(plugin);

            boolean databaseOk = ensureYaml(
                    plugin,
                    RootRecordFolders.DATABASE_CONFIG,
                    RootRecordFolders.DATABASE_CONFIG,
                    warnings);
            boolean cloudOk = ensureYaml(
                    plugin,
                    RootRecordFolders.CLOUD_CONFIG,
                    RootRecordFolders.CLOUD_CONFIG,
                    warnings);

            stampCoreMeta(plugin, warnings);
            logSummary(plugin, databaseOk, cloudOk, warnings);
            return new RepairResult(databaseOk, cloudOk, List.copyOf(warnings));
        }
    }

    private static boolean ensureYaml(
            JavaPlugin plugin, String fileName, String resourceName, List<String> warnings) {
        File file = RootRecordFolders.configFile(plugin, fileName);
        try {
            if (!file.isFile()) {
                if (!copyDefault(plugin, file, resourceName)) {
                    warnings.add(fileName + ": missing jar default; could not create");
                    return false;
                }
                plugin.getLogger().info("Created " + fileName + " under plugins/" + RootRecordFolders.FOLDER_NAME + "/");
                warnIfSecretsBlank(plugin, file, fileName, warnings);
                return true;
            }

            List<String> repaired = mergeMissingKeys(plugin, file, resourceName);
            if (!repaired.isEmpty()) {
                plugin.getLogger().info(
                        "Repaired " + fileName + " — added keys: " + String.join(", ", repaired));
            } else {
                plugin.getLogger().info(fileName + " present — no missing keys (secrets left untouched)");
            }
            warnIfSecretsBlank(plugin, file, fileName, warnings);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not ensure " + fileName, ex);
            warnings.add(fileName + ": " + ex.getMessage());
            return false;
        }
    }

    private static boolean copyDefault(JavaPlugin plugin, File target, String resourceName) throws IOException {
        try (InputStream in = openResource(plugin, resourceName)) {
            if (in == null) {
                plugin.getLogger().warning("Missing bundled " + resourceName + " default.");
                return false;
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
    }

    /**
     * Adds leaf keys from the jar default that are absent on disk.
     * Never overwrites existing keys (including blank secrets already present).
     * Protected secret keys are also skipped when present with a non-blank value
     * (defensive; merge-missing already preserves them).
     */
    private static List<String> mergeMissingKeys(JavaPlugin plugin, File file, String resourceName)
            throws IOException {
        List<String> added = new ArrayList<>();
        try (InputStream in = openResource(plugin, resourceName)) {
            if (in == null) {
                return added;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            boolean changed = false;

            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key)) {
                    continue;
                }
                if (cfg.contains(key)) {
                    if (isProtectedSecret(key) && !isBlank(cfg.getString(key))) {
                        // Explicit skip log path for operators scanning repair output.
                        continue;
                    }
                    continue;
                }
                cfg.set(key, defaults.get(key));
                added.add(key);
                changed = true;
            }

            if (changed) {
                cfg.save(file);
            }
        }
        return added;
    }

    private static void warnIfSecretsBlank(
            JavaPlugin plugin, File file, String fileName, List<String> warnings) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (RootRecordFolders.DATABASE_CONFIG.equals(fileName)) {
            String password = cfg.getString("database.password", "");
            if (isBlank(password)) {
                String msg = "database.yml password blank — set plugins/RootMC/database.yml password";
                plugin.getLogger().warning(msg);
                warnings.add(msg);
            }
        }
        if (RootRecordFolders.CLOUD_CONFIG.equals(fileName)) {
            String serverId = cfg.getString("cloud.server-id", "");
            String secret = cfg.getString("cloud.server-secret", "");
            if (isBlank(serverId) || isBlank(secret)) {
                String msg = "cloud.yml missing server-id and/or server-secret — "
                        + "set product-key in root-core.yml so Root-Core bind can fill them";
                plugin.getLogger().warning(msg);
                warnings.add(msg);
            }
        }
    }

    private static void stampCoreMeta(JavaPlugin plugin, List<String> warnings) {
        File meta = RootRecordFolders.configFile(plugin, RootRecordFolders.CORE_META);
        try {
            FileConfiguration cfg = meta.isFile()
                    ? YamlConfiguration.loadConfiguration(meta)
                    : new YamlConfiguration();
            int previous = cfg.getInt("core-connection-version", 0);
            cfg.set("core-connection-version", CORE_CONNECTION_VERSION);
            cfg.set("last-repair-epoch-ms", System.currentTimeMillis());
            cfg.save(meta);
            if (previous != CORE_CONNECTION_VERSION) {
                plugin.getLogger().info(
                        "core-connection-version: " + previous + " -> " + CORE_CONNECTION_VERSION);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + RootRecordFolders.CORE_META, ex);
            warnings.add(RootRecordFolders.CORE_META + ": " + ex.getMessage());
        }
    }

    private static void logSummary(
            JavaPlugin plugin, boolean databaseOk, boolean cloudOk, List<String> warnings) {
        plugin.getLogger().info(
                "RootMC connection ensure: database="
                        + (databaseOk ? "ok" : "FAIL")
                        + " cloud="
                        + (cloudOk ? "ok" : "FAIL")
                        + " warnings="
                        + warnings.size()
                        + " version="
                        + CORE_CONNECTION_VERSION);
    }

    /** Reads stamped version from {@code .core-meta.yml}, or {@code 0} if absent. */
    public static int readConnectionVersion(JavaPlugin plugin) {
        File meta = RootRecordFolders.configFile(plugin, RootRecordFolders.CORE_META);
        if (!meta.isFile()) {
            return 0;
        }
        return YamlConfiguration.loadConfiguration(meta).getInt("core-connection-version", 0);
    }

    private static InputStream openResource(JavaPlugin plugin, String resourceName) {
        InputStream in = plugin.getResource(resourceName);
        if (in != null) {
            return in;
        }
        // Fall back to common ClassLoader when the calling plugin did not re-export the resource.
        return RootMcCoreConnection.class.getClassLoader().getResourceAsStream(resourceName);
    }

    private static boolean isProtectedSecret(String key) {
        return PROTECTED_SECRET_KEYS.contains(key.toLowerCase(Locale.ROOT))
                || PROTECTED_SECRET_KEYS.contains(key);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
