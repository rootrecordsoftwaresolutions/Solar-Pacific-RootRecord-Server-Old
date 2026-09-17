package com.rootrecord.minecraft.common.config;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/** Shared MySQL credentials — one file for all RootMC plugins ({@code plugins/RootMC/database.yml}). */
public final class RootMcDatabaseConfig {

    public static final String FILE_NAME = "database.yml";
    public static final String DEFAULT_JDBC_PARAMS =
            "verifyServerCertificate=false&useSSL=false&allowPublicKeyRetrieval=true"
                    + "&useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC";

    private RootMcDatabaseConfig() {}

    public record DatabaseSettings(
            boolean enabled,
            String host,
            int port,
            String database,
            String username,
            String password,
            String tablePrefix,
            String jdbcParams,
            int poolSize) {

        public boolean isConfigured() {
            return host != null && !host.isBlank()
                    && database != null && !database.isBlank()
                    && username != null && !username.isBlank();
        }

        public String jdbcUrl() {
            String url = "jdbc:mysql://" + host.trim() + ":" + port + "/" + database.trim();
            if (jdbcParams != null && !jdbcParams.isBlank()) {
                url += "?" + jdbcParams.trim();
            }
            return url;
        }
    }

    /**
     * Precedence per field: plugin yaml {@code database.*} / {@code mysql.*} → {@code database.yml}
     * → legacy {@code rootmc.yml} / {@code root-essentials.yml} {@code mysql.*}.
     * Password must be set in {@code plugins/RootMC/database.yml}.
     */
    public static DatabaseSettings resolve(JavaPlugin plugin, FileConfiguration pluginConfig) {
        FileConfiguration shared = loadFile(RootRecordFolders.configFile(plugin, FILE_NAME));
        FileConfiguration rootMc = loadFile(RootRecordFolders.configFile(plugin, RootRecordFolders.ROOTMC_CONFIG));
        FileConfiguration essentials =
                loadFile(RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_ESSENTIALS_CONFIG));

        // Inherit mode (blank / localhost plugin mysql.host): ignore stub port/user/db so jar
        // defaults like 127.0.0.1:3306 cannot override plugins/RootMC/database.yml.
        String pluginHost = readString(pluginConfig, "host");
        boolean pluginOwnsEndpoint = ownsMysqlEndpoint(pluginHost);
        FileConfiguration pluginEndpoint = pluginOwnsEndpoint ? pluginConfig : null;

        String host = pickString(pluginEndpoint, shared, rootMc, essentials, "host", "127.0.0.1");
        int port = pickInt(pluginEndpoint, shared, rootMc, essentials, "port", 3306);
        String database = pickString(pluginEndpoint, shared, rootMc, essentials, "database", "minecraft");
        String username = pickString(pluginEndpoint, shared, rootMc, essentials, "username", "minecraft");
        String password = pickString(pluginEndpoint, shared, rootMc, essentials, "password", "");
        String tablePrefix = pickString(pluginConfig, shared, rootMc, essentials, "table-prefix", "root_");
        String jdbcParams = pickString(pluginConfig, shared, rootMc, essentials, "jdbc-params", DEFAULT_JDBC_PARAMS);
        int poolSize = pickInt(pluginConfig, shared, rootMc, essentials, "pool-size", 5);

        return new DatabaseSettings(
                pickEnabled(pluginConfig, shared, rootMc, essentials, true),
                host,
                port,
                database,
                username,
                password,
                tablePrefix,
                jdbcParams,
                poolSize);
    }

    /** Ensures {@code plugins/RootMC/database.yml} exists (from jar default). */
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

    private static FileConfiguration loadFile(File file) {
        return file.isFile() ? YamlConfiguration.loadConfiguration(file) : null;
    }

    private static boolean pickEnabled(
            FileConfiguration pluginCfg,
            FileConfiguration shared,
            FileConfiguration rootMc,
            FileConfiguration essentials,
            boolean defaultValue) {
        Boolean value = readEnabled(pluginCfg);
        if (value != null) {
            return value;
        }
        value = readEnabled(shared);
        if (value != null) {
            return value;
        }
        value = readEnabled(rootMc);
        if (value != null) {
            return value;
        }
        value = readEnabled(essentials);
        return value != null ? value : defaultValue;
    }

    private static Boolean readEnabled(FileConfiguration cfg) {
        if (cfg == null) {
            return null;
        }
        if (cfg.contains("database.enabled")) {
            return cfg.getBoolean("database.enabled");
        }
        if (cfg.contains("mysql.enabled")) {
            return cfg.getBoolean("mysql.enabled");
        }
        return null;
    }

    private static String pickString(
            FileConfiguration pluginCfg,
            FileConfiguration shared,
            FileConfiguration rootMc,
            FileConfiguration essentials,
            String key,
            String defaultValue) {
        String value = readString(pluginCfg, key);
        if (!value.isBlank()) {
            return value;
        }
        value = readString(shared, key);
        if (!value.isBlank()) {
            return value;
        }
        value = readString(rootMc, key);
        if (!value.isBlank()) {
            return value;
        }
        value = readString(essentials, key);
        return value.isBlank() ? defaultValue : value;
    }

    private static int pickInt(
            FileConfiguration pluginCfg,
            FileConfiguration shared,
            FileConfiguration rootMc,
            FileConfiguration essentials,
            String key,
            int defaultValue) {
        Integer value = readInt(pluginCfg, key);
        if (value != null) {
            return value;
        }
        value = readInt(shared, key);
        if (value != null) {
            return value;
        }
        value = readInt(rootMc, key);
        if (value != null) {
            return value;
        }
        value = readInt(essentials, key);
        return value != null ? value : defaultValue;
    }

    private static String readString(FileConfiguration cfg, String key) {
        if (cfg == null) {
            return "";
        }
        String databaseKey = "database." + key;
        String mysqlKey = "mysql." + key;
        if (cfg.contains(databaseKey)) {
            return nullToEmpty(cfg.getString(databaseKey));
        }
        if (cfg.contains(mysqlKey)) {
            return nullToEmpty(cfg.getString(mysqlKey));
        }
        return "";
    }

    private static Integer readInt(FileConfiguration cfg, String key) {
        if (cfg == null) {
            return null;
        }
        String databaseKey = "database." + key;
        String mysqlKey = "mysql." + key;
        if (cfg.contains(databaseKey)) {
            return cfg.getInt(databaseKey);
        }
        if (cfg.contains(mysqlKey)) {
            return cfg.getInt(mysqlKey);
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** Blank or loopback host means "use shared database.yml", not a real plugin override. */
    private static boolean ownsMysqlEndpoint(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.trim().toLowerCase();
        return !h.equals("127.0.0.1")
                && !h.equals("localhost")
                && !h.equals("::1")
                && !h.equals("0.0.0.0");
    }
}
