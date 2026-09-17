package com.rootrecord.minecraft.rootperms.config;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PermsConfig {

    public record SeedGroup(
            String id,
            String display,
            String prefix,
            int priority,
            List<String> inherits,
            List<String> nodes) {}

    private final String defaultGroup;
    private final String serverId;
    private final List<String> playerTrack;
    private final Map<String, SeedGroup> seedGroups;
    private final RootMcDatabaseConfig.DatabaseSettings database;
    private final String tablePrefix;

    private PermsConfig(
            String defaultGroup,
            String serverId,
            List<String> playerTrack,
            Map<String, SeedGroup> seedGroups,
            RootMcDatabaseConfig.DatabaseSettings database,
            String tablePrefix) {
        this.defaultGroup = defaultGroup;
        this.serverId = serverId;
        this.playerTrack = playerTrack;
        this.seedGroups = seedGroups;
        this.database = database;
        this.tablePrefix = tablePrefix;
    }

    public static PermsConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        String prefix = db.tablePrefix() == null || db.tablePrefix().isBlank() ? "root_" : db.tablePrefix();
        String configured = cfg.getString("server-id", "").trim();
        String cloudId = RootRecordCloudConfig.resolve(plugin, cfg).serverId();
        String serverId = !configured.isBlank()
                ? configured
                : (!cloudId.isBlank() ? cloudId : "local");
        List<String> track = new ArrayList<>();
        for (String raw : cfg.getStringList("player-track")) {
            if (raw != null && !raw.isBlank()) {
                track.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        Map<String, SeedGroup> seeds = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("seed-groups");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String id = key.trim().toLowerCase(Locale.ROOT);
                ConfigurationSection g = section.getConfigurationSection(key);
                if (g == null) {
                    continue;
                }
                seeds.put(
                        id,
                        new SeedGroup(
                                id,
                                g.getString("display", id),
                                g.getString("prefix", ""),
                                g.getInt("priority", 0),
                                normalizeList(g.getStringList("inherits")),
                                normalizeNodes(g.getStringList("nodes"))));
            }
        }
        return new PermsConfig(
                cfg.getString("default-group", "default").trim().toLowerCase(Locale.ROOT),
                serverId,
                List.copyOf(track),
                Map.copyOf(seeds),
                db,
                prefix);
    }

    private static List<String> normalizeList(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return List.of();
        }
        for (String item : raw) {
            if (item != null && !item.isBlank()) {
                out.add(item.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    /** Permission nodes keep {@code *} and leading {@code -} as written (lowercased). */
    private static List<String> normalizeNodes(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return List.of();
        }
        for (String item : raw) {
            if (item != null && !item.isBlank()) {
                out.add(item.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    public String defaultGroup() {
        return defaultGroup;
    }

    public String serverId() {
        return serverId;
    }

    public List<String> playerTrack() {
        return playerTrack;
    }

    public Map<String, SeedGroup> seedGroups() {
        return seedGroups;
    }

    public RootMcDatabaseConfig.DatabaseSettings database() {
        return database;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public boolean mysqlReady() {
        return database != null && database.enabled() && database.isConfigured();
    }

    public String groupsTable() {
        return tablePrefix + "perms_group";
    }

    public String groupNodesTable() {
        return tablePrefix + "perms_group_node";
    }

    public String usersTable() {
        return tablePrefix + "perms_user";
    }

    public String userNodesTable() {
        return tablePrefix + "perms_user_node";
    }
}
