package com.rootrecord.minecraft.rootupkeep.data;

import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LastLoginStore {

    private final UpkeepConfig config;

    public LastLoginStore(UpkeepConfig config) {
        this.config = config;
    }

    public Map<UUID, Instant> loadAllLastLogins() throws SQLException {
        Map<UUID, Instant> out = new HashMap<>();
        if (!config.mysqlConfigured()) {
            return out;
        }
        try (Connection c = open()) {
            ensurePlaytimeTable(c, config.playtimeTable());
            String table = config.playtimeTable();
            try {
                queryLogins(c, out, "SELECT uuid, last_login_at FROM " + table + " WHERE scope = '*'");
            } catch (SQLException ex) {
                if (!isUnknownColumn(ex, "scope")) {
                    throw ex;
                }
                queryLogins(c, out, "SELECT uuid, last_login_at FROM " + table);
            }
        } catch (SQLException ex) {
            // Soft-skip: upkeep inactivity can proceed without historical logins.
            return out;
        }
        return out;
    }

    private static void queryLogins(Connection c, Map<UUID, Instant> out, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                Timestamp ts = rs.getTimestamp("last_login_at");
                if (ts != null) {
                    out.put(uuid, ts.toInstant());
                }
            }
        }
    }

    private static boolean isUnknownColumn(SQLException ex, String column) {
        String msg = ex.getMessage();
        return msg != null
                && msg.toLowerCase().contains("unknown column")
                && msg.toLowerCase().contains(column.toLowerCase());
    }

    private static void ensurePlaytimeTable(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                CREATE TABLE IF NOT EXISTS %s (
                  uuid CHAR(36) NOT NULL,
                  scope VARCHAR(64) NOT NULL,
                  username VARCHAR(16) NOT NULL,
                  seconds BIGINT NOT NULL DEFAULT 0,
                  first_join_at DATETIME NOT NULL,
                  last_login_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  PRIMARY KEY (uuid, scope),
                  INDEX idx_playtime_scope (scope, seconds),
                  INDEX idx_playtime_username (username)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
                        .formatted(table))) {
            ps.executeUpdate();
        }
    }

    public Instant lastLogin(UUID uuid, Map<UUID, Instant> cache) {
        if (uuid == null) {
            return null;
        }
        Instant cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        return null;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(
                config.jdbcUrl(), config.mysqlUsername(), config.mysqlPassword());
    }
}
