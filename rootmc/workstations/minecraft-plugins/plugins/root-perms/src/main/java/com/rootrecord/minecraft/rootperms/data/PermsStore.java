package com.rootrecord.minecraft.rootperms.data;

import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import com.rootrecord.minecraft.rootperms.config.PermsConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PermsStore {

    public static final String SCOPE_GLOBAL = "*";
    public static final String KIND_PERMISSION = "permission";
    public static final String KIND_GROUP = "group";

    public record GroupRow(String id, String display, String prefix, int priority) {}

    public record NodeRow(String kind, String value) {}

    private final PermsConfig config;

    public PermsStore(PermsConfig config) {
        this.config = config;
    }

    public Connection open() throws SQLException {
        return MysqlConnections.open(config.database());
    }

    public void initSchema() throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement g = c.prepareStatement(
                            """
                            CREATE TABLE IF NOT EXISTS %s (
                              group_id VARCHAR(64) PRIMARY KEY,
                              display VARCHAR(64) NOT NULL,
                              prefix VARCHAR(128) NOT NULL DEFAULT '',
                              priority INT NOT NULL DEFAULT 0,
                              updated_at DATETIME NOT NULL
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
                                    .formatted(config.groupsTable()));
                    PreparedStatement gn = c.prepareStatement(
                            """
                            CREATE TABLE IF NOT EXISTS %s (
                              group_id VARCHAR(64) NOT NULL,
                              kind VARCHAR(16) NOT NULL,
                              value VARCHAR(128) NOT NULL,
                              PRIMARY KEY (group_id, kind, value),
                              INDEX idx_perms_gn_group (group_id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
                                    .formatted(config.groupNodesTable()));
                    PreparedStatement u = c.prepareStatement(
                            """
                            CREATE TABLE IF NOT EXISTS %s (
                              uuid CHAR(36) PRIMARY KEY,
                              username VARCHAR(16) NOT NULL,
                              primary_group VARCHAR(64) NOT NULL,
                              updated_at DATETIME NOT NULL
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
                                    .formatted(config.usersTable()));
                    PreparedStatement un = c.prepareStatement(
                            """
                            CREATE TABLE IF NOT EXISTS %s (
                              uuid CHAR(36) NOT NULL,
                              scope VARCHAR(64) NOT NULL,
                              kind VARCHAR(16) NOT NULL,
                              value VARCHAR(128) NOT NULL,
                              PRIMARY KEY (uuid, scope, kind, value),
                              INDEX idx_perms_un_user (uuid, scope)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                            """
                                    .formatted(config.userNodesTable()))) {
                g.executeUpdate();
                gn.executeUpdate();
                u.executeUpdate();
                un.executeUpdate();
            }
            seedGroups(c);
        }
    }

    private void seedGroups(Connection c) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        for (PermsConfig.SeedGroup seed : config.seedGroups().values()) {
            try (PreparedStatement ps = c.prepareStatement(
                    """
                    INSERT INTO %s (group_id, display, prefix, priority, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      display = VALUES(display),
                      prefix = VALUES(prefix),
                      priority = VALUES(priority),
                      updated_at = VALUES(updated_at)
                    """
                            .formatted(config.groupsTable()))) {
                ps.setString(1, seed.id());
                ps.setString(2, seed.display());
                ps.setString(3, seed.prefix() == null ? "" : seed.prefix());
                ps.setInt(4, seed.priority());
                ps.setTimestamp(5, now);
                ps.executeUpdate();
            }
            for (String parent : seed.inherits()) {
                addGroupNode(c, seed.id(), KIND_GROUP, parent);
            }
            for (String node : seed.nodes()) {
                addGroupNode(c, seed.id(), KIND_PERMISSION, node);
            }
        }
        // Default track inheritance: each rank inherits previous (optional ladder)
        List<String> track = config.playerTrack();
        for (int i = 1; i < track.size(); i++) {
            String child = track.get(i);
            String parent = track.get(i - 1);
            addGroupNode(c, child, KIND_GROUP, parent);
        }
        if (!track.isEmpty()) {
            addGroupNode(c, track.get(0), KIND_GROUP, config.defaultGroup());
        }
    }

    private void addGroupNode(Connection c, String groupId, String kind, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT IGNORE INTO %s (group_id, kind, value)
                VALUES (?, ?, ?)
                """
                        .formatted(config.groupNodesTable()))) {
            ps.setString(1, groupId.toLowerCase(Locale.ROOT));
            ps.setString(2, kind);
            // Permission wildcards / negations must keep *, -; group ids stay lowercased.
            String stored = KIND_PERMISSION.equals(kind)
                    ? value.trim().toLowerCase(Locale.ROOT)
                    : value.toLowerCase(Locale.ROOT);
            ps.setString(3, stored);
            ps.executeUpdate();
        }
    }

    public void ensureUser(UUID uuid, String username) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        String name = username == null || username.isBlank() ? "Unknown" : username;
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT INTO %s (uuid, username, primary_group, updated_at)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          username = VALUES(username),
                          updated_at = VALUES(updated_at)
                        """
                                .formatted(config.usersTable()))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, config.defaultGroup());
            ps.setTimestamp(4, now);
            ps.executeUpdate();
        }
    }

    public GroupRow group(String groupId) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT group_id, display, prefix, priority FROM "
                                + config.groupsTable()
                                + " WHERE group_id = ? LIMIT 1")) {
            ps.setString(1, groupId.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new GroupRow(
                        rs.getString("group_id"),
                        rs.getString("display"),
                        rs.getString("prefix"),
                        rs.getInt("priority"));
            }
        }
    }

    public Map<String, GroupRow> allGroups() throws SQLException {
        Map<String, GroupRow> out = new HashMap<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT group_id, display, prefix, priority FROM " + config.groupsTable());
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                GroupRow row = new GroupRow(
                        rs.getString("group_id"),
                        rs.getString("display"),
                        rs.getString("prefix"),
                        rs.getInt("priority"));
                out.put(row.id(), row);
            }
        }
        return out;
    }

    public List<NodeRow> groupNodes(String groupId) throws SQLException {
        List<NodeRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT kind, value FROM " + config.groupNodesTable() + " WHERE group_id = ?")) {
            ps.setString(1, groupId.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new NodeRow(rs.getString("kind"), rs.getString("value")));
                }
            }
        }
        return out;
    }

    public List<NodeRow> userNodes(UUID uuid, String scope) throws SQLException {
        List<NodeRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT kind, value FROM "
                                + config.userNodesTable()
                                + " WHERE uuid = ? AND scope = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, scope);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new NodeRow(rs.getString("kind"), rs.getString("value")));
                }
            }
        }
        return out;
    }

    public void addUserNode(UUID uuid, String scope, String kind, String value) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT IGNORE INTO %s (uuid, scope, kind, value)
                        VALUES (?, ?, ?, ?)
                        """
                                .formatted(config.userNodesTable()))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, scope);
            ps.setString(3, kind);
            ps.setString(4, value.toLowerCase(Locale.ROOT));
            ps.executeUpdate();
        }
    }

    public void removeUserNode(UUID uuid, String scope, String kind, String value) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM "
                                + config.userNodesTable()
                                + " WHERE uuid = ? AND scope = ? AND kind = ? AND value = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, scope);
            ps.setString(3, kind);
            ps.setString(4, value.toLowerCase(Locale.ROOT));
            ps.executeUpdate();
        }
    }

    public String primaryGroup(UUID uuid) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT primary_group FROM " + config.usersTable() + " WHERE uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return config.defaultGroup();
                }
                return rs.getString(1);
            }
        }
    }

    public void setPrimaryGroup(UUID uuid, String groupId) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + config.usersTable() + " SET primary_group = ?, updated_at = ? WHERE uuid = ?")) {
            ps.setString(1, groupId.toLowerCase(Locale.ROOT));
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    /** Effective group membership including inheritance. */
    public Set<String> resolveGroups(UUID uuid) throws SQLException {
        Set<String> direct = new LinkedHashSet<>();
        direct.add(primaryGroup(uuid));
        for (NodeRow node : userNodes(uuid, SCOPE_GLOBAL)) {
            if (KIND_GROUP.equals(node.kind())) {
                direct.add(node.value().toLowerCase(Locale.ROOT));
            }
        }
        for (NodeRow node : userNodes(uuid, config.serverId())) {
            if (KIND_GROUP.equals(node.kind())) {
                direct.add(node.value().toLowerCase(Locale.ROOT));
            }
        }
        Set<String> all = new LinkedHashSet<>();
        Map<String, List<NodeRow>> cache = new HashMap<>();
        for (String g : direct) {
            expandGroup(g, all, cache);
        }
        return all;
    }

    private void expandGroup(String groupId, Set<String> out, Map<String, List<NodeRow>> cache)
            throws SQLException {
        String id = groupId.toLowerCase(Locale.ROOT);
        if (!out.add(id)) {
            return;
        }
        List<NodeRow> nodes = cache.computeIfAbsent(id, key -> {
            try {
                return groupNodes(key);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        for (NodeRow node : nodes) {
            if (KIND_GROUP.equals(node.kind())) {
                expandGroup(node.value(), out, cache);
            }
        }
    }

    public Set<String> resolvePermissions(UUID uuid) throws SQLException {
        Set<String> groups = resolveGroups(uuid);
        Set<String> perms = new HashSet<>();
        Map<String, List<NodeRow>> cache = new HashMap<>();
        for (String g : groups) {
            List<NodeRow> nodes = cache.computeIfAbsent(g, key -> {
                try {
                    return groupNodes(key);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            for (NodeRow node : nodes) {
                if (KIND_PERMISSION.equals(node.kind())) {
                    perms.add(node.value());
                }
            }
        }
        for (NodeRow node : userNodes(uuid, SCOPE_GLOBAL)) {
            if (KIND_PERMISSION.equals(node.kind())) {
                perms.add(node.value());
            }
        }
        for (NodeRow node : userNodes(uuid, config.serverId())) {
            if (KIND_PERMISSION.equals(node.kind())) {
                perms.add(node.value());
            }
        }
        return perms;
    }
}
